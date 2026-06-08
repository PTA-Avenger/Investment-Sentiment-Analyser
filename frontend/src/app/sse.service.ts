import { Injectable, NgZone } from '@angular/core';
import { Observable } from 'rxjs';
import { SentimentService } from './sentiment.service';

@Injectable({
  providedIn: 'root'
})
export class SseService {

  constructor(private sentimentService: SentimentService, private zone: NgZone) {}

  public getEventStream(eventName: string): Observable<any> {
    return new Observable<any>(observer => {
      const baseUrl = this.sentimentService.apiUrl();
      const url = `${baseUrl}/api/sentiment/stream`;
      
      console.log(`Connecting to SSE stream: ${url}`);
      const eventSource = new EventSource(url);

      // Listener for the custom event name (e.g. 'news-update', 'status-change')
      eventSource.addEventListener(eventName, (event: MessageEvent) => {
        this.zone.run(() => {
          try {
            const data = JSON.parse(event.data);
            observer.next(data);
          } catch (e) {
            observer.next(event.data);
          }
        });
      });

      // Also listen to general connection event
      eventSource.addEventListener('connected', (event: MessageEvent) => {
        this.zone.run(() => {
          console.log('SSE connection successfully verified:', event.data);
        });
      });

      eventSource.onerror = (error) => {
        this.zone.run(() => {
          // EventSource will automatically retry, so we log it but don't terminate the stream
          console.warn('SSE stream encountered an error or disconnected. Retrying connection...', error);
          observer.next({ error: true, message: 'Stream disconnected. Reconnecting...' });
        });
      };

      // Cleanup on unsubscribe
      return () => {
        console.log(`Closing SSE stream: ${url}`);
        eventSource.close();
      };
    });
  }
}
