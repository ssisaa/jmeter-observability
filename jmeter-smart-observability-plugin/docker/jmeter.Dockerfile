# JMeter 5.6.3 with the Smart Observability plugin pre-installed
FROM eclipse-temurin:17-jre-jammy

ARG JMETER_VERSION=5.6.3

RUN apt-get update \
 && apt-get install -y --no-install-recommends curl ca-certificates \
 && rm -rf /var/lib/apt/lists/* \
 && curl -sSL "https://archive.apache.org/dist/jmeter/binaries/apache-jmeter-${JMETER_VERSION}.tgz" \
      -o /tmp/jmeter.tgz \
 && tar -xzf /tmp/jmeter.tgz -C /opt \
 && ln -s /opt/apache-jmeter-${JMETER_VERSION} /opt/jmeter \
 && rm /tmp/jmeter.tgz

ENV PATH="/opt/jmeter/bin:${PATH}"

# Install the plugin
COPY target/jmeter-smart-observability-plugin-*.jar /opt/jmeter/lib/ext/

WORKDIR /work
ENTRYPOINT ["jmeter"]
