$url = "https://archive.apache.org/dist/maven/maven-3/3.9.6/binaries/apache-maven-3.9.6-bin.zip"
$output = "c:\InvestmentSentimentAnalyser\maven.zip"
Write-Host "Downloading Maven from $url..."
Invoke-WebRequest -Uri $url -OutFile $output
Write-Host "Download complete. Extracting Maven..."
Expand-Archive -Path $output -DestinationPath "c:\InvestmentSentimentAnalyser"
Remove-Item $output
if (Test-Path "c:\InvestmentSentimentAnalyser\apache-maven-3.9.6") {
    Rename-Item -Path "c:\InvestmentSentimentAnalyser\apache-maven-3.9.6" -NewName "maven"
}
Write-Host "Maven set up successfully."
