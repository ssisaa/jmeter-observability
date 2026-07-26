import { useEffect, useState } from "react";
import "@/App.css";
import { BrowserRouter, Routes, Route } from "react-router-dom";
import axios from "axios";

const BACKEND_URL = process.env.REACT_APP_BACKEND_URL;
const API = `${BACKEND_URL}/api`;

const formatBytes = (bytes) => {
  if (!bytes) return "0 B";
  const units = ["B", "KB", "MB", "GB"];
  let i = 0;
  let v = bytes;
  while (v >= 1024 && i < units.length - 1) {
    v /= 1024;
    i += 1;
  }
  return `${v.toFixed(v < 10 && i > 0 ? 2 : 0)} ${units[i]}`;
};

const Section = ({ title, children, testId }) => (
  <section data-testid={testId} className="section">
    <h2>{title}</h2>
    {children}
  </section>
);

const DownloadRow = ({ item, testId }) => (
  <a
    className="download-row"
    href={`${BACKEND_URL}${item.url}`}
    data-testid={testId}
    download
  >
    <span className="download-name">{item.name}</span>
    <span className="download-size">{formatBytes(item.size_bytes)}</span>
    <span className="download-cta">Download &rarr;</span>
  </a>
);

const Home = () => {
  const [info, setInfo] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    axios
      .get(`${API}/plugin/info`)
      .then((r) => setInfo(r.data))
      .catch((e) => setError(String(e)));
  }, []);

  return (
    <div className="App-page">
      <header className="hero">
        <div className="tag">Smart Observability AI</div>
        <h1>JMeter Smart Observability Plugin</h1>
        <p className="subtitle">
          Enterprise Performance Test Reporting Framework &middot; v2.0.7
        </p>
      </header>

      <main className="page-main">
        {error && (
          <div data-testid="load-error" className="error-banner">
            Failed to load plugin info: {error}
          </div>
        )}

        <Section title="Plugin binary" testId="section-plugin-binary">
          {info?.jar ? (
            <DownloadRow item={info.jar} testId="download-plugin-jar" />
          ) : (
            <p className="muted" data-testid="jar-empty">
              Plugin jar not built yet. Run <code>mvn clean package</code> in
              <code> /app/jmeter-smart-observability-plugin</code>.
            </p>
          )}
        </Section>

        <Section title="Public demo report" testId="section-demo">
          {info?.demos && info.demos.length > 0 ? (
            <div className="download-list">
              {info.demos.map((d, i) => (
                <DownloadRow
                  key={d.name}
                  item={d}
                  testId={`download-demo-${i}`}
                />
              ))}
            </div>
          ) : (
            <p className="muted" data-testid="demo-empty">
              Demo assets not yet generated. Run{" "}
              <code>java -cp target/*.jar com.smartjmeter.demo.DemoReport docs/demo</code>.
            </p>
          )}
        </Section>

        <Section title="Notifier smoke deck" testId="section-smoke">
          {info?.smoke && info.smoke.length > 0 ? (
            <div className="download-list">
              {info.smoke.map((d, i) => (
                <DownloadRow
                  key={d.name}
                  item={d}
                  testId={`download-smoke-${i}`}
                />
              ))}
            </div>
          ) : (
            <p className="muted">Smoke deck files not found.</p>
          )}
        </Section>

        <Section title="Documentation" testId="section-docs">
          {info?.docs && info.docs.length > 0 ? (
            <div className="download-list">
              {info.docs.map((d, i) => (
                <DownloadRow
                  key={d.name}
                  item={d}
                  testId={`download-docs-${i}`}
                />
              ))}
            </div>
          ) : (
            <p className="muted">No docs found.</p>
          )}
        </Section>

        <Section title="Docker Compose demo bundle" testId="section-docker">
          {info?.docker && info.docker.length > 0 ? (
            <div className="download-list">
              {info.docker.map((d, i) => (
                <DownloadRow
                  key={d.name}
                  item={d}
                  testId={`download-docker-${i}`}
                />
              ))}
            </div>
          ) : (
            <p className="muted">Docker bundle not found.</p>
          )}
        </Section>

        <Section title="What's new in v2.0.7" testId="section-changelog">
          <ul className="changelog">
            <li>
              <b>Splunk O11y endpoint fixed (real fix)</b> &mdash; now uses{" "}
              <code>GET /v1/timeserieswindow</code>, the only bounded
              batch endpoint. The previous <code>/v2/timeserieswindow</code>{" "}
              does not exist (404) and SignalFlow was ruled out because it
              streams indefinitely, unsuitable for test-window collection.
            </li>
            <li>
              <b>Bottleneck logs during teardown</b> &mdash; every rule-engine
              finding is now logged with severity, category, confidence and
              evidence, so operators see exactly what tripped without opening
              the report.
            </li>
            <li>
              <b>Metric spec is friendlier</b> &mdash; bare names
              (<code>cpu.utilization</code>) are auto-wrapped as{" "}
              <code>sf_metric:"..."</code>; SignalFlow programs are
              downgraded to the metric name; raw filter expressions
              (<code>sf_metric:... AND host:...</code>) are passed through.
            </li>
            <li>
              <b>Resolution auto-snap</b> &mdash; requested resolution is
              rounded UP to the nearest valid bucket
              (1s / 1m / 5m / 1h) so the API never rejects the call.
            </li>
          </ul>
        </Section>
      </main>

      <footer className="page-footer">
        Direct endpoints:{" "}
        <code>/api/downloads/plugin.jar</code>,{" "}
        <code>/api/downloads/demo/*</code>,{" "}
        <code>/api/downloads/smoke/*</code>,{" "}
        <code>/api/downloads/docs/*</code>,{" "}
        <code>/api/downloads/docker/*</code>
      </footer>
    </div>
  );
};

function App() {
  return (
    <div className="App">
      <BrowserRouter>
        <Routes>
          <Route path="/" element={<Home />} />
        </Routes>
      </BrowserRouter>
    </div>
  );
}

export default App;
