# المنفذ #24 — SANAD Solution Architecture & Service Decomposition Platform

## الحالة

```text
Executor #23: APPROVED
Gate #23: PASSED
Executor #24: IN EXECUTION
```

## الغرض

تحويل SANAD من Backlog تنفيذي معتمد إلى معمارية خدمات قابلة للبناء والتشغيل والتوسع، مع تحديد:

- Domains
- Subdomains
- Bounded Contexts
- Microservices / Modular Services
- API Contracts
- Event Contracts
- Service Ownership
- Runtime Dependencies
- Data Ownership
- Security Boundaries
- SLO Targets

## أساس التنفيذ

يعتمد هذا المنفذ على مخرجات المنفذ #23 المعتمدة، ويستخدم معرفات Backlog كمدخل رسمي لتفكيك الخدمات وربطها بالمنصات والموجات والأولويات.

## المخرجات القابلة للتوليد

يشغّل الأمر التالي مولد التفكيك المعماري:

```bash
python3 scripts/generate_service_decomposition.py
```

وينتج داخل `generated/executor-24/`:

- `sanad-service-catalog.csv`
- `sanad-service-catalog.json`
- `sanad-api-catalog.csv`
- `sanad-event-catalog.csv`
- `sanad-domain-ownership.csv`
- `sanad-service-dependency-matrix.csv`
- `sanad-service-decomposition-summary.json`

## الحجم المعماري الناتج

| المخرج | العدد |
|---|---:|
| Domains | 13 |
| Bounded Contexts | 78 |
| Services | 468 |
| APIs | 1,404 |
| Events | 468 |
| Dependency Edges | 756 |

## Domains المعتمدة

1. SaaS Core
2. Security Governance
3. Workflow Automation
4. AI Core and Agents
5. Customer Relationship Management
6. ERP Operations
7. Accounting and Finance
8. Human Resources
9. Ecommerce and CX
10. POS and Industry Engine
11. Enterprise Data and Analytics
12. Platform Operations
13. Partner Marketplace

## نمط التفكيك

لكل Domain يتم تحديد ستة أنواع خدمات قياسية حول كل Aggregate رئيسي:

- Command Service
- Query Service
- Workflow Service
- Integration Service
- Analytics Service
- Admin Service

هذا النمط يضمن الفصل بين الكتابة والقراءة، التشغيل والتحليل، التكامل والإدارة، مع قابلية التطوير التدريجي دون فرض Microservices مبكرة لكل جزء.

## حدود الملكية

كل خدمة تملك:

- Service ID ثابت.
- Domain وSubdomain وBounded Context.
- Owner Squad.
- System of Record flag.
- Database Ownership.
- Primary Aggregate.
- Criticality.
- MVP flag.
- Deployment Unit.
- Sync Dependencies وAsync Dependencies.
- Security Boundary.
- Data Classification.
- SLO Target.
- Backlog References.

## قواعد البيانات والملكية

- خدمات Command هي System of Record.
- خدمات Query تملك Read Models وProjections فقط.
- خدمات Analytics تعتمد على Event Streams ولا تكتب في سجلات التشغيل الرسمية.
- يمنع مشاركة قاعدة بيانات تشغيلية بين Domains دون عقد تكامل رسمي.
- التكامل بين الخدمات يتم عبر APIs أو Events فقط.

## قواعد APIs

تستخدم جميع APIs:

- Versioning عبر `/api/v1`.
- Auth Scopes دقيقة حسب Domain وAggregate وAction.
- Idempotency للعمليات الكتابية.
- Rate Limit tiers حسب Criticality.
- عقود قابلة للتوثيق لاحقًا في OpenAPI.

## قواعد Events

لكل Command Service يتم نشر الأحداث القياسية:

- Created
- Updated
- StatusChanged
- Approved
- Rejected
- Archived

تستخدم الأحداث Topics معيارية بصيغة:

```text
sanad.<domain>.<aggregate>.<event>
```

## قواعد الاعتمادية

- الاعتماد الأمني المركزي يتم عبر Security Governance.
- الاعتماد على معلومات المستأجر والمؤسسة يتم عبر SaaS Core.
- الاعتماد التشغيلي على سير العمل يتم عبر Workflow Automation.
- الاعتماد التحليلي يتم عبر Enterprise Data and Analytics.
- الاعتمادات المتزامنة تستخدم APIs.
- الاعتمادات غير المتزامنة تستخدم Events وReplay وDead-letter.

## SLO

- خدمات P0 وP1 تستهدف 99.95%.
- خدمات P2 تستهدف 99.90%.
- لا يتحول أي SLO إلى التزام تجاري قبل اعتماد بوابة الإنتاج التجاري.

## العلاقة مع المنفذ #25

يستخدم المنفذ #25 هذه المخرجات لتحديد:

- Squads
- Service Owners
- Platform Teams
- On-call responsibilities
- Team Topologies
- Engineering Operating Model

## قرار البوابة

لا يُعد هذا المنفذ معتمدًا بمجرد الدمج. يلزم:

1. نجاح Workflow التحقق.
2. مراجعة Service Catalog.
3. مراجعة Dependencies.
4. اعتماد Domain Ownership.
5. موافقة المالك على فتح المنفذ #25.
