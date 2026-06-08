# JSE Investment Sentiment Analyser Automation Script
# This script builds the Angular frontend, copies static assets to Spring Boot, packages the JAR, and runs it.

$ErrorActionPreference = "Stop"

# 1. Environment Verification
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "   ENVIRONMENT SETUP & VERIFICATION" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

# Configure Maven path locally
$localMavenBin = "c:\InvestmentSentimentAnalyser\maven\bin"
if (Test-Path $localMavenBin) {
    $env:Path = "$localMavenBin;$env:Path"
    Write-Host "Local Maven added to environment PATH." -ForegroundColor Green
} else {
    Write-Warning "Local Maven folder not found. Relying on global 'mvn' command."
}

# Verify Java, Node, and NPM
Write-Host "Checking environment versions..." -ForegroundColor Yellow
java -version
Write-Host "Node version: $(node -v)" -ForegroundColor Green
Write-Host "NPM version: $(npm -v)" -ForegroundColor Green

# 2. Build Angular Frontend
Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "   BUILDING ANGULAR FRONTEND" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

Push-Location "c:\InvestmentSentimentAnalyser\frontend"
try {
    Write-Host "Running npm install in frontend..." -ForegroundColor Yellow
    npm install
    
    Write-Host "Compiling Angular frontend for production..." -ForegroundColor Yellow
    npm run build:prod
    Write-Host "Frontend build and assets transfer completed successfully!" -ForegroundColor Green
} catch {
    Write-Error "Frontend compilation failed: $_"
} finally {
    Pop-Location
}

# 3. Build Spring Boot Backend
Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "   BUILDING SPRING BOOT BACKEND" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

Push-Location "c:\InvestmentSentimentAnalyser\backend"
try {
    Write-Host "Packaging Java application with Maven..." -ForegroundColor Yellow
    mvn clean package -DskipTests
    Write-Host "Backend packaged successfully!" -ForegroundColor Green
} catch {
    Write-Error "Backend packaging failed: $_"
} finally {
    Pop-Location
}

# 4. Launch Executable Application
Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "   LAUNCHING JSE SENTIMENT ANALYSER" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Starting Spring Boot JAR with 3GB Heap (-Xmx3g) for Stanford CoreNLP..." -ForegroundColor Green
Write-Host "Access the application at: http://localhost:8081" -ForegroundColor Cyan
Write-Host "Press Ctrl+C to terminate the application." -ForegroundColor Yellow
Write-Host "----------------------------------------"

java -Xmx3g -jar c:\InvestmentSentimentAnalyser\backend\target\backend-0.0.1-SNAPSHOT.jar
