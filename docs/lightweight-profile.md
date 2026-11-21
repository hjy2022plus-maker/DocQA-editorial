# Lightweight dissertation profile

The `lightweight` Spring profile is the default runtime and implements the boundaries in `修改方案.md`:

- MySQL and Elasticsearch remain the only external stores; originals are kept
  on local disk under `app.storage.root`.
- Upload is a normal multipart request with MD5 de-duplication, not chunks or
  resumable transfer. Tika/PDFBox parses PDF, Word, Markdown, and TXT files.
- Parsing is asynchronous and uses overlapping sliding-window chunks. Questions
  stream through SSE and include citation events; insufficient evidence emits a
  refusal event.
- JWT authentication remains in place. Public documents are visible to all
  users and private documents are restricted to their owner. Organization tags
  and multi-tenant filters are not part of this profile.
- The answer transport is SSE. The client receives answer fragments and
  citation events; reconnecting and generation snapshots are intentionally out
  of scope.
- The evaluation scope is four question types: factual QA, exact term match,
  cross-paragraph synthesis, and no-answer refusal. Record first-token and
  end-to-end latency instead of running concurrency or recovery experiments.

Start with `SPRING_PROFILES_ACTIVE=lightweight` (or leave it unset to use the
default). The dissertation demo does not require Kafka, MinIO, Redis, OCR, or
WebSocket infrastructure.

The six thesis sections are:

1. Document knowledge-base construction and semantic indexing.
2. Hybrid retrieval (BM25 + KNN + RRF) and reranking.
3. Context expansion, citation mapping, and the interaction system.
4. Evaluation-set construction and comparison/ablation experiments.
5. Key technical difficulties: parsing/chunking, hybrid recall, and hallucination control.
6. Innovation and application value: traceable citations and refusal rules.
