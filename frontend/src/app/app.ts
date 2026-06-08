import { Component, OnInit, OnDestroy, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { SentimentService } from './sentiment.service';
import { SseService } from './sse.service';
import { Subscription } from 'rxjs';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App implements OnInit, OnDestroy {
  // Title
  protected readonly title = signal('JSE Sentiment Dashboard');

  // Application Data Signals
  public sectors = signal<any[]>([]);
  public headlines = signal<any[]>([]);
  public status = signal<any>({
    nlpState: 'LOADING',
    initTimeMs: 0,
    failureReason: '',
    totalRequests: 0,
    fallbackRequests: 0,
    averageLatencyMs: 0,
    pipelineLogs: []
  });

  // UI state
  public activeSectorFilter = signal<string>('All');
  public searchQuery = signal<string>('');
  public isSidebarOpen = signal<boolean>(false);
  
  // Custom Sentiment Simulator variables
  public simulatorText = signal<string>('');
  public simulatorResult = signal<any | null>(null);
  public isSimulating = signal<boolean>(false);

  // Administrative/Settings state
  public isIngesting = signal<boolean>(false);
  public apiUrlInput = signal<string>('');
  public showSettings = signal<boolean>(false);
  public operationMessage = signal<string | null>(null);

  // Subscriptions list for cleanup
  private subscriptions: Subscription[] = [];

  constructor(
    public sentimentService: SentimentService,
    private sseService: SseService
  ) {
    this.apiUrlInput.set(this.sentimentService.apiUrl());
  }

  ngOnInit(): void {
    // 1. Fetch initial statistics and status
    this.loadAllData();

    // 2. Initialize SSE stream subscriptions
    this.setupEventStreams();
  }

  ngOnDestroy(): void {
    // Unsubscribe all active streams to prevent memory leaks
    this.subscriptions.forEach(s => s.unsubscribe());
  }

  // Reload all dashboard panels
  public loadAllData(): void {
    this.errorMessage = null;
    
    this.sentimentService.getSectorStats().subscribe({
      next: (data) => this.sectors.set(data),
      error: (err) => this.handleApiError('Failed to fetch JSE sector statistics.', err)
    });

    this.sentimentService.getHeadlines().subscribe({
      next: (data) => this.headlines.set(data),
      error: (err) => this.handleApiError('Failed to fetch news headlines.', err)
    });

    this.sentimentService.getStatus().subscribe({
      next: (data) => this.status.set(data),
      error: (err) => this.handleApiError('Failed to fetch system operational status.', err)
    });
  }

  // Set up event source listener streams
  private setupEventStreams(): void {
    // Listen to database update notifications
    const updateSub = this.sseService.getEventStream('news-update').subscribe({
      next: (event) => {
        console.log('SSE Ingestion Event Received:', event);
        this.showTemporaryMessage(event.message || 'Database updated in real-time.');
        this.loadAllData();
      },
      error: (err) => console.error('SSE News Update Error:', err)
    });
    this.subscriptions.push(updateSub);

    // Listen to nlp engine state change notifications
    const statusSub = this.sseService.getEventStream('status-change').subscribe({
      next: (event) => {
        console.log('SSE NLP Status Event Received:', event);
        this.showTemporaryMessage(event.message || 'NLP Engine configuration changed.');
        // Update local state details
        this.sentimentService.getStatus().subscribe(data => this.status.set(data));
      },
      error: (err) => console.error('SSE Status Change Error:', err)
    });
    this.subscriptions.push(statusSub);
  }

  // Filter headlines based on search query and active sector selection
  public filteredHeadlines = computed(() => {
    let list = this.headlines();
    const query = this.searchQuery().toLowerCase().trim();
    const sector = this.activeSectorFilter();

    if (sector !== 'All') {
      list = list.filter(h => h.sector && h.sector.toLowerCase() === sector.toLowerCase());
    }

    if (query) {
      list = list.filter(h => 
        (h.title && h.title.toLowerCase().includes(query)) ||
        (h.description && h.description.toLowerCase().includes(query)) ||
        (h.source && h.source.toLowerCase().includes(query))
      );
    }

    return list;
  });

  // Calculate market outlook: average of all sector scores weighted
  public marketOutlook = computed(() => {
    const list = this.sectors();
    if (list.length === 0) return { label: 'NEUTRAL', score: 0.0, class: 'neutral' };

    let totalScore = 0;
    let validSectors = 0;
    
    list.forEach(s => {
      if (s.totalCount > 0) {
        totalScore += s.averageScore;
        validSectors++;
      }
    });

    const avg = validSectors === 0 ? 0.0 : totalScore / validSectors;
    
    if (avg > 0.15) {
      return { label: 'BULLISH', score: avg, class: 'bullish' };
    } else if (avg < -0.15) {
      return { label: 'BEARISH', score: avg, class: 'bearish' };
    } else {
      return { label: 'NEUTRAL', score: avg, class: 'neutral' };
    }
  });

  // Test Custom Sentiment (Simulator Workbench)
  public runSimulator(): void {
    const text = this.simulatorText().trim();
    if (!text) return;

    this.isSimulating.set(true);
    this.simulatorResult.set(null);

    this.sentimentService.analyzeText(text).subscribe({
      next: (result) => {
        this.simulatorResult.set(result);
        this.isSimulating.set(false);
      },
      error: (err) => {
        console.error('Simulator run failed:', err);
        this.isSimulating.set(false);
        this.handleApiError('Sentiment simulation workbench failed.', err);
      }
    });
  }

  // Toggle CoreNLP Engine Status
  public toggleNlpEngine(currentState: string): void {
    const targetState = currentState !== 'DISABLED';
    
    // Call controller to disable or enable
    this.sentimentService.toggleCoreNlp(!targetState).subscribe({
      next: (res) => {
        this.showTemporaryMessage(res.nlpState === 'DISABLED' ? 'CoreNLP disabled. Fallback active.' : 'CoreNLP initialization triggered.');
        this.loadAllData();
      },
      error: (err) => this.handleApiError('Failed to change NLP engine configuration.', err)
    });
  }

  // Trigger manual scraper execution
  public triggerScraper(): void {
    if (this.isIngesting()) return;
    this.isIngesting.set(true);
    this.showTemporaryMessage('News ingestion pipeline triggered in background...');

    this.sentimentService.runIngest().subscribe({
      next: () => {
        // We set a timer to reset the button spinner.
        // The SSE connection will trigger the data reload when completed.
        setTimeout(() => this.isIngesting.set(false), 3000);
      },
      error: (err) => {
        this.isIngesting.set(false);
        this.handleApiError('Failed to run news scraper.', err);
      }
    });
  }

  // Reset database and seed default values
  public runSeeder(): void {
    if (!confirm('Are you sure you want to purge the H2 database and reload mock JSE sector headlines? This will reset all current records.')) {
      return;
    }

    this.showTemporaryMessage('Purging database and reloading JSE mock data...');
    this.sentimentService.seedData().subscribe({
      next: () => {
        this.showTemporaryMessage('Mock JSE headlines loaded successfully!');
        this.loadAllData();
      },
      error: (err) => this.handleApiError('Database seeding failed.', err)
    });
  }

  // Manage API Server Url Customization
  public saveApiSettings(): void {
    const inputUrl = this.apiUrlInput().trim();
    if (!inputUrl) {
      alert('Please enter a valid HTTP/HTTPS URL.');
      return;
    }

    this.sentimentService.updateApiUrl(inputUrl);
    this.showSettings.set(false);
    this.showTemporaryMessage(`Connected to endpoint: ${inputUrl}`);
    
    // Re-initialize subscriptions and load data for new URL
    this.loadAllData();
    this.subscriptions.forEach(s => s.unsubscribe());
    this.subscriptions = [];
    this.setupEventStreams();
  }

  public resetApiSettings(): void {
    localStorage.removeItem('jse_backend_url');
    // Force recalculate default
    const defaultUrl = this.sentimentService['resolveApiUrl']();
    this.sentimentService.apiUrl.set(defaultUrl);
    this.apiUrlInput.set(defaultUrl);
    this.showSettings.set(false);
    this.showTemporaryMessage('API endpoint reset to defaults.');
    
    // Re-initialize
    this.loadAllData();
    this.subscriptions.forEach(s => s.unsubscribe());
    this.subscriptions = [];
    this.setupEventStreams();
  }

  // Notification and Error Display helpers
  public errorMessage: string | null = null;

  private handleApiError(context: string, error: any): void {
    console.error(context, error);
    this.errorMessage = `${context} Server is currently unreachable. Check endpoint configuration in Settings.`;
  }

  private showTemporaryMessage(msg: string): void {
    this.operationMessage.set(msg);
    setTimeout(() => {
      // Clear if it matches the current message
      if (this.operationMessage() === msg) {
        this.operationMessage.set(null);
      }
    }, 4500);
  }

  // Helper method for rendering sentiment styles
  public getScoreColorClass(score: number): string {
    if (score > 0.15) return 'emerald';
    if (score < -0.15) return 'crimson';
    return 'amber';
  }

  public getScoreLabel(score: number): string {
    if (score > 0.15) return 'POSITIVE';
    if (score < -0.15) return 'NEGATIVE';
    return 'NEUTRAL';
  }

  public formatPercentage(count: number, total: number): number {
    if (!total) return 0;
    return Math.round((count / total) * 100);
  }
}
