# Users Production Certification Image Trigger

This inert release-control marker exists only to ensure that the protected merge of the Users production-governance workflow publishes an immutable backend image tagged with the exact resulting `main` SHA.

It changes no application runtime behavior, database schema, tenant data, RBAC semantics, or production configuration.

Production deployment remains manual and fail-closed through `Users Production Release`; certification remains separate through `Users Production Certification`.
