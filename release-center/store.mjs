import fs from 'node:fs'
import path from 'node:path'
import { DatabaseSync } from 'node:sqlite'

/**
 * 发布中心的本地作业与发布凭证存储。
 *
 * 页面只依赖这里的查询结果，不再把活动任务和最近一次任务放在进程变量里。
 */
export class ReleaseStore {
  constructor(file) {
    fs.mkdirSync(path.dirname(file), { recursive: true, mode: 0o700 })
    this.database = new DatabaseSync(file)
    try {
      fs.chmodSync(file, 0o600)
    } catch {
      // 非 POSIX 文件系统继续使用目录权限保护数据库
    }
    this.database.exec('PRAGMA journal_mode = WAL')
    this.database.exec('PRAGMA busy_timeout = 5000')
    this.migrate()
  }

  migrate() {
    this.database.exec(`
      CREATE TABLE IF NOT EXISTS release_job (
        id            TEXT PRIMARY KEY,
        version       TEXT NOT NULL,
        kind          TEXT NOT NULL,
        title         TEXT,
        status        TEXT NOT NULL,
        step          TEXT,
        logs_json     TEXT NOT NULL,
        error         TEXT,
        commit_sha    TEXT,
        tag_name      TEXT,
        metadata_json TEXT NOT NULL,
        created_at    TEXT NOT NULL,
        updated_at    TEXT NOT NULL
      );

      CREATE INDEX IF NOT EXISTS idx_release_job_updated
        ON release_job(updated_at DESC);

      CREATE TABLE IF NOT EXISTS release_record (
        version          TEXT PRIMARY KEY,
        job_id           TEXT NOT NULL,
        release_code     TEXT,
        channel          TEXT NOT NULL,
        source_commit    TEXT,
        commit_sha       TEXT NOT NULL,
        tag_name         TEXT,
        manifest_path    TEXT,
        manifest_sha256  TEXT,
        artifacts_json   TEXT NOT NULL,
        created_at       TEXT NOT NULL
      );

      CREATE INDEX IF NOT EXISTS idx_release_record_created
        ON release_record(created_at DESC);

      CREATE TABLE IF NOT EXISTS deployment_record (
        id             TEXT PRIMARY KEY,
        job_id         TEXT NOT NULL,
        environment    TEXT NOT NULL,
        target_url     TEXT,
        status         TEXT NOT NULL,
        error          TEXT,
        created_at     TEXT NOT NULL,
        updated_at     TEXT NOT NULL
      );

      CREATE INDEX IF NOT EXISTS idx_deployment_record_updated
        ON deployment_record(updated_at DESC);
    `)
  }

  upsertJob(job) {
    const statement = this.database.prepare(`
      INSERT INTO release_job (
        id, version, kind, title, status, step, logs_json, error,
        commit_sha, tag_name, metadata_json, created_at, updated_at
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      ON CONFLICT(id) DO UPDATE SET
        version = excluded.version,
        kind = excluded.kind,
        title = excluded.title,
        status = excluded.status,
        step = excluded.step,
        logs_json = excluded.logs_json,
        error = excluded.error,
        commit_sha = excluded.commit_sha,
        tag_name = excluded.tag_name,
        metadata_json = excluded.metadata_json,
        updated_at = excluded.updated_at
    `)
    statement.run(
      job.id,
      job.version,
      job.kind || 'publish',
      job.title || null,
      job.status,
      job.step || null,
      JSON.stringify(Array.isArray(job.logs) ? job.logs : []),
      job.error || null,
      job.commitSha || null,
      job.tagName || null,
      JSON.stringify(job.metadata || {}),
      job.createdAt,
      job.updatedAt,
    )
    return job
  }

  getJob(id) {
    const row = this.database.prepare(`
      SELECT * FROM release_job WHERE id = ?
    `).get(id)
    return row ? toJob(row) : null
  }

  getLastJob() {
    const row = this.database.prepare(`
      SELECT * FROM release_job ORDER BY updated_at DESC LIMIT 1
    `).get()
    return row ? toJob(row) : null
  }

  listJobs(limit = 20) {
    const safeLimit = Math.max(1, Math.min(Number(limit) || 20, 100))
    return this.database.prepare(`
      SELECT * FROM release_job ORDER BY updated_at DESC LIMIT ?
    `).all(safeLimit).map(toJob)
  }

  interruptRunningJobs(reason, now = new Date().toISOString()) {
    const result = this.database.prepare(`
      UPDATE release_job
      SET status = 'interrupted',
          step = 'interrupted',
          error = ?,
          updated_at = ?
      WHERE status = 'running'
    `).run(reason, now)
    return Number(result.changes || 0)
  }

  saveRelease(record) {
    this.database.prepare(`
      INSERT INTO release_record (
        version, job_id, release_code, channel, source_commit, commit_sha,
        tag_name, manifest_path, manifest_sha256, artifacts_json, created_at
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      ON CONFLICT(version) DO UPDATE SET
        job_id = excluded.job_id,
        release_code = excluded.release_code,
        channel = excluded.channel,
        source_commit = excluded.source_commit,
        commit_sha = excluded.commit_sha,
        tag_name = excluded.tag_name,
        manifest_path = excluded.manifest_path,
        manifest_sha256 = excluded.manifest_sha256,
        artifacts_json = excluded.artifacts_json,
        created_at = excluded.created_at
    `).run(
      record.version,
      record.jobId,
      record.releaseCode || null,
      record.channel,
      record.sourceCommit || null,
      record.commitSha,
      record.tagName || null,
      record.manifestPath || null,
      record.manifestSha256 || null,
      JSON.stringify(Array.isArray(record.artifacts) ? record.artifacts : []),
      record.createdAt,
    )
    return record
  }

  getRelease(version) {
    const row = this.database.prepare(`
      SELECT * FROM release_record WHERE version = ?
    `).get(version)
    return row ? toRelease(row) : null
  }

  listReleases(limit = 20) {
    const safeLimit = Math.max(1, Math.min(Number(limit) || 20, 100))
    return this.database.prepare(`
      SELECT * FROM release_record ORDER BY created_at DESC LIMIT ?
    `).all(safeLimit).map(toRelease)
  }

  saveDeployment(record) {
    this.database.prepare(`
      INSERT INTO deployment_record (
        id, job_id, environment, target_url, status, error, created_at, updated_at
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
      ON CONFLICT(id) DO UPDATE SET
        environment = excluded.environment,
        target_url = excluded.target_url,
        status = excluded.status,
        error = excluded.error,
        updated_at = excluded.updated_at
    `).run(
      record.id,
      record.jobId,
      record.environment,
      record.targetUrl || null,
      record.status,
      record.error || null,
      record.createdAt,
      record.updatedAt,
    )
    return record
  }

  listDeployments(limit = 20) {
    const safeLimit = Math.max(1, Math.min(Number(limit) || 20, 100))
    return this.database.prepare(`
      SELECT * FROM deployment_record ORDER BY updated_at DESC LIMIT ?
    `).all(safeLimit).map((row) => ({
      id: row.id,
      jobId: row.job_id,
      environment: row.environment,
      targetUrl: row.target_url,
      status: row.status,
      error: row.error,
      createdAt: row.created_at,
      updatedAt: row.updated_at,
    }))
  }

  close() {
    this.database.close()
  }
}

function toJob(row) {
  return {
    id: row.id,
    version: row.version,
    kind: row.kind,
    title: row.title,
    status: row.status,
    step: row.step,
    logs: parseJson(row.logs_json, []),
    error: row.error,
    commitSha: row.commit_sha,
    tagName: row.tag_name,
    metadata: parseJson(row.metadata_json, {}),
    createdAt: row.created_at,
    updatedAt: row.updated_at,
  }
}

function toRelease(row) {
  return {
    version: row.version,
    jobId: row.job_id,
    releaseCode: row.release_code,
    channel: row.channel,
    sourceCommit: row.source_commit,
    commitSha: row.commit_sha,
    tagName: row.tag_name,
    manifestPath: row.manifest_path,
    manifestSha256: row.manifest_sha256,
    artifacts: parseJson(row.artifacts_json, []),
    createdAt: row.created_at,
  }
}

function parseJson(value, fallback) {
  try {
    return JSON.parse(value)
  } catch {
    return fallback
  }
}
