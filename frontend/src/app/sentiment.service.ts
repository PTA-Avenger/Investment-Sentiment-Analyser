import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class SentimentService {
  
  // Dynamic API Base URL state
  private readonly localStorageKey = 'jse_backend_url';
  public readonly apiUrl = signal(this.resolveApiUrl());

  constructor(private http: HttpClient) {}

  private resolveApiUrl(): string {
    const savedUrl = localStorage.getItem(this.localStorageKey);
    if (savedUrl) {
      return savedUrl;
    }
    
    // Default fallback: if running locally, point to port 8080.
    if (typeof window !== 'undefined' && 
        (window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1')) {
      return 'http://localhost:8081';
    }
    
    // Default production placeholder (user can edit this in the Admin Panel)
    return 'https://jse-sentiment-analyser.onrender.com';
  }

  public updateApiUrl(newUrl: string): void {
    let cleanUrl = newUrl.trim();
    if (cleanUrl.endsWith('/')) {
      cleanUrl = cleanUrl.slice(0, -1);
    }
    localStorage.setItem(this.localStorageKey, cleanUrl);
    this.apiUrl.set(cleanUrl);
  }

  public getSectorStats(): Observable<any[]> {
    return this.http.get<any[]>(`${this.apiUrl()}/api/sentiment/sectors`);
  }

  public getHeadlines(): Observable<any[]> {
    return this.http.get<any[]>(`${this.apiUrl()}/api/sentiment/headlines`);
  }

  public getStatus(): Observable<any> {
    return this.http.get<any>(`${this.apiUrl()}/api/sentiment/status`);
  }

  public analyzeText(text: string): Observable<any> {
    return this.http.post<any>(`${this.apiUrl()}/api/sentiment/analyze`, { text });
  }

  public toggleCoreNlp(enabled: boolean): Observable<any> {
    return this.http.post<any>(`${this.apiUrl()}/api/sentiment/toggle`, { enabled });
  }

  public runIngest(): Observable<any> {
    return this.http.post<any>(`${this.apiUrl()}/api/sentiment/ingest`, {});
  }

  public seedData(): Observable<any> {
    return this.http.post<any>(`${this.apiUrl()}/api/sentiment/seed`, {});
  }
}
