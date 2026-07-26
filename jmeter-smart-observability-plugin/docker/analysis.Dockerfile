# Analysis Service - shared LLM + rule pipeline
FROM eclipse-temurin:17-jre-jammy

RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY target/jmeter-smart-observability-plugin-*.jar /app/plugin.jar

EXPOSE 7788

# Falls back to a static-analysis payload when ANALYSIS_LLM_API_KEY is blank.
CMD ["java", "-cp", "/app/plugin.jar", "com.smartjmeter.analysis.AnalysisServer"]
