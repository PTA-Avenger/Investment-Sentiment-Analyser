# Using the exact bootVersion supported by start.spring.io
$url = "https://start.spring.io/starter.zip?type=maven-project&language=java&bootVersion=3.5.14.RELEASE&baseDir=backend&groupId=com.jse.sentiment&artifactId=backend&name=InvestmentSentimentAnalyser&description=Investment+Sentiment+Analyser+for+JSE&packageName=com.jse.sentiment&packaging=jar&javaVersion=17&dependencies=web,data-jpa,h2"
$output = "c:\InvestmentSentimentAnalyser\backend.zip"
Write-Host "Downloading Spring Boot starter zip from Spring Initializr..."
try {
    Invoke-WebRequest -Uri $url -OutFile $output
    Write-Host "Extracting Spring Boot project..."
    Expand-Archive -Path $output -DestinationPath "c:\InvestmentSentimentAnalyser"
    Remove-Item $output
    Write-Host "Spring Boot project bootstrapped successfully."
} catch {
    Write-Error "Failed to download: $_"
}
