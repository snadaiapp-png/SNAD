# Import Mapping — Jira / Azure DevOps / GitHub Projects

## المصدر

بعد تشغيل:

```bash
python3 scripts/generate_master_execution_backlog.py
```

يستخدم الملف الأساسي:

```text
generated/executor-23/sanad-master-execution-backlog.csv
```

## Jira

| CSV field | Jira field |
|---|---|
| External ID | External issue ID أو حقل مخصص `SANAD External ID` |
| Issue Type | Issue Type |
| Summary | Summary |
| Description | Description |
| Epic Name | Epic Name |
| Epic Link | Epic Link أو Parent بحسب إصدار Jira |
| Parent External ID | Parent |
| Owner Role | Assignee role custom field |
| Owner Squad | Team |
| Priority | Priority |
| Story Points | Story Points |
| Original Estimate Hours | Original Estimate بعد تحويل القيمة إلى hours |
| Release | Fix Version/s |
| Target Sprint | Sprint |
| Component | Component/s |
| Labels | Labels |
| Dependencies | Issue Links بعد مرحلة الاستيراد الأولى |
| Acceptance Criteria | Acceptance Criteria custom field |
| Definition of Ready | DoR custom field |
| Definition of Done | DoD custom field |

### ترتيب استيراد Jira

1. Epics.
2. Features.
3. Stories.
4. Tasks.
5. ربط Dependencies باستخدام External ID.
6. إنشاء Releases وSprints وربطها.
7. تشغيل فحص العدد والتبعيات والعينات.

## Azure DevOps

| CSV field | Azure DevOps field |
|---|---|
| External ID | Custom.SANADExternalID |
| Issue Type | Work Item Type |
| Summary | Title |
| Description | Description |
| Parent External ID | Parent link بعد الاستيراد |
| Owner Squad | Area Path أو Team |
| Target Sprint | Iteration Path |
| Story Points | Story Points / Effort |
| Priority | Priority |
| Acceptance Criteria | Acceptance Criteria |
| Release | Tags أو Release custom field |
| Labels | Tags |

يستخدم التسلسل:

```text
Epic → Feature → User Story → Task
```

## GitHub Projects

يمثل كل عنصر Issue، وتستخدم الحقول:

- `Issue Type`
- `Platform`
- `Portfolio`
- `Owner Squad`
- `Priority`
- `Size`
- `Story Points`
- `MVP`
- `Release`
- `Execution Wave`
- `Target Sprint`
- `External ID`

يضاف Parent External ID وDependencies إلى جسم الـIssue أو إلى علاقات Parent/Sub-issue وBlocked by عند تفعيلها.

## التحقق بعد الاستيراد

يجب أن تكون الأعداد النهائية على الأقل:

```text
Epics: 154
Features: 924
Stories: 4,620
Tasks: 18,480
Total: 24,178
```

ويجب أن تحقق الفحوصات التالية:

- لا يوجد External ID مكرر.
- جميع Parent External IDs موجودة.
- كل Feature مرتبطة بـEpic.
- كل Story مرتبطة بـFeature.
- كل Task مرتبطة بـStory.
- كل عنصر مرتبط بمنصة وSquad وPriority وRelease وSprint.
- جميع عناصر MVP تحمل P0 أو P1 وفق قواعد الحوكمة.
- لا يبدأ تنفيذ عنصر قبل إغلاق Dependencies الحرجة.