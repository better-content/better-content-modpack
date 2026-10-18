/** User-owned: never modify managed herdr-agent-state.ts.
 * Claims survive automatic continuations until agent_settled. No disposal.
 */
import { execFileSync } from "node:child_process";
import { randomUUID } from "node:crypto";
import { homedir } from "node:os";
import { join } from "node:path";
import { Type } from "@earendil-works/pi-ai";
import type { ExtensionAPI } from "@earendil-works/pi-coding-agent";

export default function (pi: ExtensionAPI) {
  const script = process.env.WORKSPACE_COORDINATION_SCRIPT ?? join(homedir(), "better-content-modpack/scripts/workspace_coordination.py");
  const maintenance = process.env.WORKSPACE_COORDINATION_MAINTENANCE_SCRIPT ?? join(homedir(), "better-content-modpack/scripts/workspace-maintenance.py");
  const root = process.env.WORKSPACE_COORDINATION_ROOT ?? join(homedir(), ".worklane/coordination");
  const owner = `${process.env.HERDR_SESSION ?? "outside"}:${process.env.HERDR_PANE_ID ?? "outside"}:${process.pid}:${randomUUID()}`;
  const task = process.env.WORKSPACE_COORDINATION_TASK;
  const generation = process.env.WORKSPACE_COORDINATION_GENERATION;
  let turnSession = process.env.PI_SESSION_ID ?? "";
  let claimed = false;
  let denied = false;
  let areas = (process.env.WORKSPACE_COORDINATION_AREAS ?? "").split(",").filter(Boolean);
  const warned = new Set<string>();
  const run = (...args: string[]) => JSON.parse(execFileSync("python3", ["-B", script, "--root", root, ...args], {
    encoding: "utf8", timeout: 10000, stdio: ["ignore", "pipe", "pipe"],
  }));
  const authority = () => {
    if (task || generation) {
      if (!task || !generation) throw new Error("Both task and generation required");
      run("authority", "--task", task, "--generation", generation);
    }
  };
  const acquire = (paths: string[], cwd: string, extend = false) => {
    authority();
    const args = paths.flatMap(a => ["--area", a]);
    args.push("--actor-pid", String(process.pid));
    if (turnSession) args.push("--session-id", turnSession);
    if (task && generation) args.push("--task", task, "--generation", generation);
    if (extend) args.push("--extend");
    return run("turn-acquire", "--owner", owner, "--cwd", cwd, ...args);
  };
  const release = () => {
    if (claimed) {
      // On failure keep claimed=true; never pretend an unreleased claim vanished.
      run("turn-release", "--owner", owner);
      claimed = false;
    }
  };
  pi.registerCommand("coordination-areas", {
    description: "Declare shared writer paths or token:NAME areas for subsequent turns",
    handler: async (args, ctx) => {
      if (claimed) { ctx.ui.notify("Cannot replace areas during an active turn", "warning"); return; }
      areas = args.split(",").map(a => a.trim()).filter(Boolean);
      ctx.ui.notify(`Coordination areas: ${areas.join(", ") || "identity only; bash blocked until declaration"}`, "info");
    },
  });
  pi.registerTool({
    name: "coordination_claim", label: "Declare writer areas",
    description: "Atomically extend intended writer paths or semantic/resource scopes before bash; no whole-cwd lock required. Write/edit exact paths are also claimed automatically. Shell effects remain actor-responsible; not a sandbox.",
    parameters: Type.Object({ areas: Type.Array(Type.String(), { minItems: 1 }) }),
    async execute(_id, params, _signal, _update, ctx) {
      if (!claimed || denied) throw new Error("No admitted coordination turn");
      const result = acquire(params.areas, ctx.cwd, true);
      return { content: [{type:"text", text:JSON.stringify(result)}], details:result };
    },
  });
  pi.on("input", async (_event, ctx) => {
    try { authority(); } catch (e) {
      denied = true;
      if (ctx.hasUI) ctx.ui.notify(`Cancelled or superseded task generation: ${String(e)}`, "warning");
      return { action: "handled" };
    }
  });
  pi.on("before_agent_start", async (_event, ctx) => {
    if (claimed) return;
    denied = false;
    try {
      authority();
      const session = process.env.HERDR_SESSION;
      const agents = session ? JSON.parse(execFileSync("herdr", ["--session", session, "agent", "list"], {
        encoding: "utf8", timeout: 10000,
      })).result.agents.map((a: any) => ({ pane: a.pane_id, status: a.agent_status })) : "outside Herdr";
      turnSession = ctx.sessionManager.getSessionId();
      acquire(areas.length ? areas : [`identity:${owner}`], ctx.cwd);
      claimed = true;
      const live = run("live");
      let storageWarnings: string[] = [];
      try {
        const status = JSON.parse(execFileSync("python3", ["-B", maintenance, "status", "--cached"], {
          encoding:"utf8", timeout:5000, stdio:["ignore","pipe","pipe"],
        }));
        storageWarnings = (status.warnings ?? []).map(String).filter((w: string) => !warned.has(w));
      } catch { storageWarnings = warned.has("Storage status unavailable (non-gating)") ? [] : ["Storage status unavailable (non-gating)"]; }
      for (const warning of storageWarnings) {
        warned.add(warning);
        if (ctx.hasUI) ctx.ui.notify(warning, "warning");
      }
      return { message: { customType: "workspace-coordination", content: JSON.stringify({ owner, areas, agents, live, storageWarnings }), display: false } };
    } catch (e) {
      denied = true;
      ctx.abort();
      if (ctx.hasUI) ctx.ui.notify(`Coordination admission denied; retry after owner/maintenance yields: ${String(e)}`, "warning");
    }
  });
  pi.on("tool_call", async (event, ctx) => {
    try { authority(); } catch { denied = true; ctx.abort(); }
    if (denied || !claimed) return { block: true, reason: "No admitted current-generation turn" };
    const name = event.toolName.split(/[.:]/).pop();
    const input = event.input as Record<string, unknown>;
    try {
      if (name === "write" || name === "edit") {
        if (typeof input.path !== "string") throw new Error("Writer must declare exact path");
        acquire([input.path], ctx.cwd, true);
      } else if (name === "bash" || name === "user_bash") {
        // Explicit intended scope, not a shell-effect parser or whole-cwd mutex.
        run("turn-scope", "--owner", owner);
      }
    } catch (e) {
      return { block:true, reason:`Writer scope unavailable. Use coordination_claim for actual intended paths/resources before bash; do not mutate outside declared scope. ${String(e)}` };
    }
  });
  // Direct human !/!! commands stay outside automatic agent claims. Do not
  // intercept user_bash: this integration coordinates agents, not human shell access.
  // Deliberately no agent_end handler: retries/compaction/follow-ups retain claim.
  pi.on("agent_settled", async () => { release(); denied = false; });
  pi.on("session_shutdown", async () => { release(); });
}
