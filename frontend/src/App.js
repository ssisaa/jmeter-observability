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
          Enterprise Performance Test Reporting Framework &middot; v2.0.3
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

        <Section title="What's new in v2.0.3" testId="section-changelog">
          <ul className="changelog">
            <li>
              <b>Rolling baselines</b> &mdash; auto-prune snapshots older than{" "}
              <code>Baseline_History_Max_Days</code> and cap at{" "}
              <code>Baseline_History_Max</code>.
            </li>
            <li>
              <b>Alert cooldowns</b> &mdash; per-sink+verdict throttle so the
              same NO_GO won't page ServiceNow twice.
            </li>
            <li>
              <b>Analysis service split</b> &mdash; run{" "}
              <code>com.smartjmeter.analysis.AnalysisServer</code> once,
              point every JMeter runner at it via{" "}
              <code>Analysis_Service_Url</code>.
            </li>
            <li>
              <b>Public demo report</b> &mdash; one-command HTML/PDF/PPTX/JSON
              generator (<code>com.smartjmeter.demo.DemoReport</code>).
            </li>
          </ul>
        </Section>
      </main>

      <footer className="page-footer">
        Direct download endpoints:{" "}
        <code>/api/downloads/plugin.jar</code>,{" "}
        <code>/api/downloads/demo/*</code>,{" "}
        <code>/api/downloads/smoke/*</code>
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
