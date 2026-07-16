#!/usr/bin/env python3
from pathlib import Path

path = Path('docs/crm/CRM-CURRENT-BASELINE.md')
text = path.read_text(encoding='utf-8')

api_marker = '| PATCH | `/api/v1/crm/accounts/{accountId}/restore` | `CRM.ACCOUNT.ARCHIVE` | `IMPLEMENTED_AND_CONNECTED` |\n'
api_rows = '''| GET | `/api/v1/crm/accounts/{accountId}/master` | `CRM.ACCOUNT.READ` | `IMPLEMENTED_AND_CONNECTED` |
| PATCH | `/api/v1/crm/accounts/{accountId}/master` | `CRM.ACCOUNT.WRITE` | `IMPLEMENTED_AND_CONNECTED` |
| GET/POST | `/api/v1/crm/accounts/{accountId}/addresses` | `CRM.ACCOUNT.READ/WRITE` | `IMPLEMENTED_AND_CONNECTED` |
| DELETE | `/api/v1/crm/accounts/{accountId}/addresses/{addressId}` | `CRM.ACCOUNT.WRITE` | `IMPLEMENTED_AND_CONNECTED` |
| GET/POST | `/api/v1/crm/accounts/{accountId}/identifiers` | `CRM.ACCOUNT.READ/WRITE` | `IMPLEMENTED_AND_CONNECTED` |
| GET/POST | `/api/v1/crm/accounts/{accountId}/relationships` | `CRM.ACCOUNT.READ/WRITE` | `IMPLEMENTED_AND_CONNECTED` |
| GET | `/api/v1/crm/accounts/{accountId}/duplicates` | `CRM.ACCOUNT.READ` | `IMPLEMENTED_AND_CONNECTED` |
| POST | `/api/v1/crm/accounts/{sourceAccountId}/merge/{targetAccountId}` | `CRM.ACCOUNT.WRITE` | `IMPLEMENTED_AND_CONNECTED` |
'''
if 'accounts/{accountId}/master' not in text:
    if api_marker not in text:
        raise SystemExit('API marker not found')
    text = text.replace(api_marker, api_marker + api_rows, 1)

migration_marker = '| `20260716.3` | `V20260716_3__create_crm_tags.sql`'
migration_row = '| `20260716.4` | `V20260716_4__crm_enterprise_account_customer_master.sql` | Extends the Account golden record with enterprise identity, customer classification, addresses, identifiers, relationships, duplicate detection, merge history, risk, credit and data-quality governance. | `IMPLEMENTED_AND_CONNECTED` |\n'
if 'V20260716_4__crm_enterprise_account_customer_master.sql' not in text:
    lines = text.splitlines(keepends=True)
    for index, line in enumerate(lines):
        if line.startswith(migration_marker):
            lines.insert(index + 1, migration_row)
            text = ''.join(lines)
            break
    else:
        raise SystemExit('Migration marker not found')

text = text.replace('> **Last reconciled:** 2026-07-12', '> **Last reconciled:** 2026-07-16', 1)
path.write_text(text, encoding='utf-8')
