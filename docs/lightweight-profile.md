# Lightweight dissertation profile

The `lightweight` Spring profile implements the boundaries in `修改方案.md`:

- MySQL and Elasticsearch remain the only external stores; originals are kept
  on local disk under `app.storage.root`.
- Upload is a normal multipart request with MD5 de-duplication, not chunks or
  resumable transfer. Tika/PDFBox parses PDF, Word, Markdown, and TXT files.
- Parsing is asynchronous and uses overlapping sliding-window chunks. Questions
  stream through SSE and include citation events; insufficient evidence emits a
  refusal event.
- JWT authentication remains in place. Public documents are visible to all
  users and private documents are restricted to their owner.

Start with `SPRING_PROFILES_ACTIVE=lightweight`. The original enterprise
profile is kept only for backwards compatibility; the dissertation demo does
not require Kafka, MinIO, Redis, OCR, or WebSocket infrastructure.
