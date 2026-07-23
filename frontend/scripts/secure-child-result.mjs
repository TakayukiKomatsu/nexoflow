function childExitCode(result) {
  if (result.error || result.signal || typeof result.status !== "number") {
    return 1;
  }
  return result.status;
}

export function secureRunnerExitCode(testRun, evidenceScan) {
  const testExitCode = childExitCode(testRun);
  return testExitCode !== 0 ? testExitCode : childExitCode(evidenceScan);
}
