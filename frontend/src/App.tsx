import { ChangeEvent, useEffect, useMemo, useState } from "react";
import {
  DemoRun,
  DemoRunRow,
  DemoRunSummary,
  DemoScenarioFile,
  fetchRecentRuns,
  fetchRun,
  fetchRunSummary,
  fetchScenarios,
  ingestScenario,
  ingestFile
} from "./api";

type RunState = {
  artifactId: string;
  correlationId: string;
  summary: DemoRunSummary;
  run: DemoRun;
};

const statusClass: Record<string, string> = {
  PASS: "status-pass",
  NEEDS_REVIEW: "status-review",
  BLOCKING: "status-blocking"
};

export function App() {
  const [scenarios, setScenarios] = useState<DemoScenarioFile[]>([]);
  const [runs, setRuns] = useState<DemoRunRow[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [runState, setRunState] = useState<RunState | null>(null);

  useEffect(() => {
    void bootstrap();
  }, []);

  async function bootstrap() {
    try {
      const [scenarioData, runData] = await Promise.all([fetchScenarios(), fetchRecentRuns()]);
      setScenarios(scenarioData);
      setRuns(runData);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to load demo data");
    }
  }

  async function runScenario(scenario: DemoScenarioFile) {
    setLoading(true);
    setError(null);
    try {
      const ingest = await ingestScenario(scenario.scenario, scenario.fileName);
      await hydrateRunState(ingest.artifactId, ingest.correlationId);
      const runData = await fetchRecentRuns();
      setRuns(runData);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Scenario run failed");
    } finally {
      setLoading(false);
    }
  }

  async function runWithFile(file: File) {
    const ingest = await ingestFile(file);
    await hydrateRunState(ingest.artifactId, ingest.correlationId);
  }

  async function hydrateRunState(artifactId: string, correlationId: string) {
    let summary: DemoRunSummary | null = null;
    let run: DemoRun | null = null;
    for (let i = 0; i < 30; i++) {
      summary = await fetchRunSummary(correlationId);
      run = await fetchRun(correlationId);
      const terminal = run.events.some((event) =>
        [
          "FilingSubmittedEvent",
          "FilingSubmissionFailedEvent",
          "ValidationExceptionRaisedEvent",
          "RuleExceptionRaisedEvent"
        ].includes(event.eventType)
      );
      if (terminal || run.events.length > 8) {
        break;
      }
      await new Promise((resolve) => setTimeout(resolve, 800));
    }
    if (!summary || !run) {
      throw new Error("Engine did not return a run state");
    }
    setRunState({
      artifactId,
      correlationId,
      summary,
      run
    });
  }

  async function onCustomFile(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    if (!file) {
      return;
    }
    setLoading(true);
    setError(null);
    try {
      await runWithFile(file);
      const runData = await fetchRecentRuns();
      setRuns(runData);
    } catch (e) {
      setError(e instanceof Error ? e.message : "File run failed");
    } finally {
      setLoading(false);
      event.target.value = "";
    }
  }

  const groupedScenarios = useMemo(() => {
    return scenarios.reduce<Record<string, DemoScenarioFile[]>>((acc, item) => {
      if (!acc[item.scenario]) {
        acc[item.scenario] = [];
      }
      acc[item.scenario].push(item);
      return acc;
    }, {});
  }, [scenarios]);

  return (
    <div className="page">
      <header>
        <h1>Balfouriana Demo Console</h1>
        <p>Upload demo fund files, run engine flow, and inspect outcomes.</p>
      </header>

      {error ? <div className="error">{error}</div> : null}

      <section className="card">
        <h2>Scenario Launcher</h2>
        <div className="scenario-grid">
          {Object.entries(groupedScenarios).map(([scenario, files]) => (
            <div className="scenario-card" key={scenario}>
              <h3>{scenario}</h3>
              {files.map((file) => (
                <button key={file.relativePath} disabled={loading} onClick={() => runScenario(file)}>
                  Run {file.fileName}
                </button>
              ))}
            </div>
          ))}
        </div>
        <div className="upload-row">
          <label htmlFor="custom-file">Manual upload</label>
          <input id="custom-file" type="file" onChange={onCustomFile} />
        </div>
      </section>

      <section className="card">
        <h2>Latest Runs</h2>
        <table>
          <thead>
            <tr>
              <th>Correlation ID</th>
              <th>Artifact ID</th>
              <th>Status</th>
              <th>Occurred At</th>
            </tr>
          </thead>
          <tbody>
            {runs.map((run) => (
              <tr key={run.correlationId}>
                <td>{run.correlationId}</td>
                <td>{run.artifactId || "-"}</td>
                <td>
                  <span className={`status ${statusClass[run.status]}`}>{run.status}</span>
                </td>
                <td>{new Date(run.occurredAt).toLocaleString()}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>

      {runState ? (
        <>
          <section className="card">
            <h2>Run Outcome</h2>
            <div className="meta-grid">
              <div>
                <strong>Correlation ID</strong>
                <div>{runState.correlationId}</div>
              </div>
              <div>
                <strong>Artifact ID</strong>
                <div>{runState.artifactId}</div>
              </div>
              <div>
                <strong>Status</strong>
                <div>
                  <span className={`status ${statusClass[runState.summary.status]}`}>{runState.summary.status}</span>
                </div>
              </div>
              <div>
                <strong>Review / Blocking</strong>
                <div>
                  {runState.summary.reviewCount} / {runState.summary.blockingCount}
                </div>
              </div>
            </div>
            <div>
              <strong>Reason Codes</strong>
              <ul>
                {runState.summary.reasonCodes.length === 0 ? <li>None</li> : null}
                {runState.summary.reasonCodes.map((code) => (
                  <li key={code}>{code}</li>
                ))}
              </ul>
            </div>
            <div>
              <strong>Step 4</strong>
              <div>
                generated: {String(runState.summary.step4.generated)} | submitted: {String(runState.summary.step4.submitted)} | ack:{" "}
                {runState.summary.step4.ackStatus || "null"}
              </div>
            </div>
          </section>

          <section className="card">
            <h2>Audit Timeline</h2>
            <ol className="timeline">
              {runState.run.events.map((event, index) => (
                <li key={`${event.eventType}-${index}`}>
                  <div>
                    <strong>{event.eventType}</strong> <span>({event.schemaVersion})</span>
                  </div>
                  <div>{new Date(event.occurredAt).toLocaleString()}</div>
                </li>
              ))}
            </ol>
          </section>
        </>
      ) : null}
    </div>
  );
}
