export type IngestResponse = {
  artifactId: string;
  correlationId: string;
  storedPath: string;
};

export type DemoScenarioFile = {
  scenario: string;
  fileName: string;
  relativePath: string;
};

export type DemoRunSummary = {
  correlationId: string;
  artifactId: string | null;
  status: "PASS" | "NEEDS_REVIEW" | "BLOCKING";
  reviewCount: number;
  blockingCount: number;
  reasonCodes: string[];
  step4: {
    generated: boolean;
    submitted: boolean;
    ackStatus: "ACK" | "NACK" | "UNRESOLVED" | null;
  };
};

export type DemoRunEvent = {
  eventType: string;
  occurredAt: string;
  schemaVersion: string;
  payload: Record<string, unknown>;
};

export type DemoRun = {
  correlationId: string;
  events: DemoRunEvent[];
};

export type DemoRunRow = {
  correlationId: string;
  artifactId: string | null;
  status: "PASS" | "NEEDS_REVIEW" | "BLOCKING";
  occurredAt: string;
};

const baseUrl = import.meta.env.ENGINE_API_BASE_URL || "http://localhost:8080";

export async function fetchScenarios(): Promise<DemoScenarioFile[]> {
  const response = await fetch(`${baseUrl}/demo/scenarios`);
  if (!response.ok) {
    throw new Error(`Failed to fetch scenarios: ${response.status}`);
  }
  return response.json();
}

export async function fetchScenarioFile(scenario: string, fileName: string): Promise<Blob> {
  const response = await fetch(`${baseUrl}/demo/scenarios/${encodeURIComponent(scenario)}/${encodeURIComponent(fileName)}`);
  if (!response.ok) {
    throw new Error(`Failed to fetch scenario file: ${response.status}`);
  }
  return response.blob();
}

export async function ingestScenario(scenario: string, fileName: string): Promise<IngestResponse> {
  const response = await fetch(
    `${baseUrl}/demo/scenarios/${encodeURIComponent(scenario)}/${encodeURIComponent(fileName)}/ingest`
  );
  if (!response.ok) {
    throw new Error(`Failed to ingest scenario: ${response.status}`);
  }
  return response.json();
}

export async function ingestFile(file: File): Promise<IngestResponse> {
  const formData = new FormData();
  formData.append("file", file);
  const response = await fetch(`${baseUrl}/ingest`, {
    method: "POST",
    body: formData
  });
  if (!response.ok) {
    throw new Error(`Failed to ingest file: ${response.status}`);
  }
  return response.json();
}

export async function fetchRunSummary(correlationId: string): Promise<DemoRunSummary> {
  const response = await fetch(`${baseUrl}/demo/runs/${correlationId}/summary`);
  if (!response.ok) {
    throw new Error(`Failed to fetch summary: ${response.status}`);
  }
  return response.json();
}

export async function fetchRun(correlationId: string): Promise<DemoRun> {
  const response = await fetch(`${baseUrl}/demo/runs/${correlationId}`);
  if (!response.ok) {
    throw new Error(`Failed to fetch run: ${response.status}`);
  }
  return response.json();
}

export async function fetchRecentRuns(limit = 20): Promise<DemoRunRow[]> {
  const response = await fetch(`${baseUrl}/demo/runs?limit=${limit}`);
  if (!response.ok) {
    throw new Error(`Failed to fetch runs: ${response.status}`);
  }
  return response.json();
}
