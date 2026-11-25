# Ledger CSV format

The CSV files both Ledger apps write and read. CSV is the only way to move data between iOS and Android, so a file
exported on one platform must import on the other unchanged (D12 in [platform-decisions.md](platform-decisions.md)).

This document is meant to be kept in both repositories. Android drafted it on 25 November 2026 from iOS `1748a53`
(`models/CSVExporter.swift`, `models/CSVImporter.swift`) and Android's `domain/csv/`, for the iOS developer to
review. At that commit the two apps write and read the files the same way, apart from the small difference in
[Differences between the apps](#differences-between-the-apps).

**Shared test files.** iOS's `DemoData/` holds a year of data in all ten files, written by `DemoData/generate.py`.
Both apps must import every line, and exporting the result must give the same lines back. Android checks this in
`IosDemoDataTest`, on a copy in `app/src/test/resources/ios-demo-data`.

**Changing the format:**

- Change both apps, `DemoData` and this document together, and say so in the commit (D14).
- Prefer adding a column to renaming or removing one. Importers ignore columns they don't know, so older apps keep
  reading newer files.
- A new column must not make a header match an earlier rule in [How a file's type is found](#how-a-files-type-is-found).
  For example, a `limit` column in any file other than `budgets.csv` would turn that file into budgets.

## Files

Export writes one file per kind of data and leaves out kinds with nothing in them. Import takes any number of files
at once, in any order and under any name, because the type comes from the header.

| File | One row per | Header |
| --- | --- | --- |
| `expenses.csv` | expense | `date;amount;source;category;details` |
| `incomes.csv` | income | `date;amount;destination;category;details` |
| `transfers.csv` | transfer | `date;amount;source;target;details` |
| `accounts.csv` | account | `name;initial_value;icon;color` |
| `categories.csv` | category | `name;kind;icon;color` |
| `budgets.csv` | monthly budget | `category;limit;active` |
| `planned_transactions.csv` | planned transaction | `kind;amount;source;destination;category;frequency;interval;start_date;end_date;paused;details` |
| `collections.csv` | collection | `name;icon;color` |
| `collection_transactions.csv` | transaction in a collection | `collection;` + the [transaction reference](#transaction-references) |
| `receipt_items.csv` | receipt item | the [transaction reference](#transaction-references) + `;position;name;quantity;unit_price;total_price` |

`collections.csv` and `collection_transactions.csv` are always written together, even when no collection has
transactions.

**Not in the files:**

- receipt photos and PDFs (iOS issue #15; Android's phone-to-phone transfer, D2, does carry them);
- the time of day of transactions, only the date;
- IDs, so a transaction is identified by its contents (see [transaction references](#transaction-references));
- the link from a transaction to the planned transaction that recorded it;
- the remembered merchant → category and account suggestions;
- when a budget alert last went off;
- settings: app lock, reminders, language, Pro status.

## Text and lines

**Writing:**

- UTF-8 without a byte order mark, `\n` after every line including the last.
- Fields are separated by `;` and never quoted. Instead, in every value:
  - `;` becomes `,`;
  - `"` becomes `'`;
  - each line break becomes a space.
- The header uses the lower-case column names above.

**Reading:**

- The text is read as UTF-8, then Windows-1250 (common in Croatian bank exports), then ISO Latin-1, which accepts any
  bytes. A byte order mark is ignored.
- Any line break style works (`\n`, `\r\n`, `\r`). Blank lines are ignored.
- The first non-blank line is the header. Column names are case-insensitive, may come in any order, and unknown
  columns are ignored.
- Every field is trimmed of spaces and of `"` at either end, so quoted fields from other apps work as long as they
  contain no `;`.
- An empty field counts as missing, and a missing column counts as empty in every row.

## Values

| Kind of value | Written as | Read |
| --- | --- | --- |
| Date | `dd.MM.yyyy` in the phone's time zone: `01.11.2025` | The same, with or without a final dot (`01.11.2025.`). Android also reads `1.11.2025`; not checked on iOS. Imported transactions are at 00:00 that day. |
| Amount | A plain decimal with a dot, no thousands separator, no trailing zeros: `12.5`, `1000`, `0.99` | Every character except digits, `,`, `.` and `-` is dropped, so `€ 12,50` works. With a comma, the comma is the decimal separator and dots are thousands separators (`1.234,56`); without one, the dot is the decimal separator. |
| Sign | Never negative, except an account's `initial_value` | Negative amounts become positive, except `initial_value`. The file or the `kind` gives the direction. |
| Yes or no | `true` or `false` | `true`, `yes` or `1` in any case is yes; anything else is no; empty takes the default. |
| Transaction kind | `expense`, `income`, `transfer` | Lower case only. |
| Category kind | `expense`, `income` | Lower case only. |
| Frequency | `daily`, `weekly`, `monthly`, `yearly` | Lower case only. `interval` multiplies it: `weekly` with `2` is every two weeks. |
| Colour | `primary` (the text colour), `red`, `orange`, `yellow`, `green`, `mint`, `teal`, `cyan`, `blue`, `indigo`, `purple`, `pink`, `brown`, `gray` | An unknown name takes the default colour. |
| Icon | An SF Symbol name from iOS's `Icons.swift` (79 names), for example `cart` or `fork.knife` | Any name is kept. Android maps the 79 names to its own icons (`ui/theme/Icons.kt`) and shows a tag for any other. |

**Amount pitfalls** for files from other apps: `1,234.56` is read as 1.23456, and `1.234` as 1.234, not 1234.

## Each file

A row is **skipped** when a required value is missing or unreadable. Skipped rows are reported by file and line
number (the header is line 1, and blank lines count). Rows describing something that already exists are **ignored**,
which isn't reported.

Names of accounts, categories and collections are compared **ignoring case and surrounding spaces**. A category also
has to have the same kind. An account or category that a row names but doesn't exist yet is created with the
defaults below.

### Transactions: `expenses.csv`, `incomes.csv`, `transfers.csv`

| Column | Expense | Income | Transfer |
| --- | --- | --- | --- |
| `date` | required | required | required |
| `amount` | required | required | required |
| `source` | the account it's paid from, required | (not used) | the account it leaves; `source` or `target` required |
| `destination` | (not used) | the account it's paid into, required | (not used) |
| `target` | (not used) | (not used) | the account it goes to |
| `category` | an expense category, optional; the column itself must be in the header | an income category, optional; the column must be in the header | (not used) |
| `details` | the note, optional | the note, optional | the note, optional |

- Transactions are **always added**, never matched: importing the same file twice gives every transaction twice.
- Export sorts them by date.

### `accounts.csv`

- `name` is required. `initial_value` defaults to 0 and may be negative.
- Defaults: icon `creditcard`, colour `blue`.
- An account that exists already is left unchanged, including its initial value.
- Export sorts accounts by when they were created. Import creates them in file order, so the order is kept.

### `categories.csv`

- `name` and `kind` are required.
- Defaults: icon `tag`, colour `primary`.
- An existing category with the same name and kind is left unchanged.
- Export sorts categories by name.

### `budgets.csv`

- `limit` is required. `category` names an expense category; empty means the general budget for all spending. `active`
  defaults to yes.
- One budget per category. If one exists already, the row is ignored and the existing budget keeps its limit (iOS
  issue #19 proposes taking the imported values).
- Export sorts budgets by category name, so the general budget comes first.

### `planned_transactions.csv`

- `kind`, `amount`, `frequency`, `start_date` and `source` or `destination` are required.
- The accounts follow the kind: an expense uses `source`, an income `destination`, a transfer both. Note that a
  transfer's second account is `destination` here, not `target`.
- `category` follows the kind as for transactions; a transfer has none.
- Optional: `interval` (default 1; anything below 1 becomes 1), `end_date` (none), `paused` (no), `details`.
- A plan is ignored when one exists with the same kind, amount, accounts, frequency, interval, start date and details.
- **Past occurrences count as recorded.** Every occurrence due before the import (up to `end_date`), even of a paused
  plan, counts as already recorded, because the exported transactions contain it. The plan only records occurrences
  after the import. Importing this file without the transaction files therefore loses the past occurrences.
- Export sorts plans by start date.

### `collections.csv`

- `name` is required.
- Defaults: icon `globe`, colour `blue`.
- An existing collection with the same name is left unchanged.
- Export sorts collections by name.

### `collection_transactions.csv`

- `collection` and the [transaction reference](#transaction-references) are required. A collection that doesn't exist
  yet is created with the defaults.
- The row adds the first matching transaction that isn't in the collection yet. When every match is already in it,
  the row is ignored. So two identical transactions take two rows.
- A row that matches no transaction is skipped.
- Export sorts rows by collection name, then by date.

### `receipt_items.csv`

- `name` and the [transaction reference](#transaction-references) are required. The item goes to the first matching
  transaction; a row that matches none is skipped.
- Defaults: `position` 0 (the item's place on the receipt, from 0), `quantity` 1, `unit_price` and `total_price` 0.
- The row is ignored when that transaction already has an item with the same position and name.
- Export sorts rows by the transaction's date, then by position.

## Transaction references

Exported transactions have no IDs, so `collection_transactions.csv` and `receipt_items.csv` name a transaction by six
columns:

| Column | Value |
| --- | --- |
| `transaction_date` | the date |
| `transaction_kind` | `expense`, `income` or `transfer` |
| `transaction_amount` | the amount |
| `transaction_source` | the account it leaves: an expense's or transfer's source, empty for an income |
| `transaction_destination` | the account it goes to: an income's destination or a transfer's target, empty for an expense |
| `transaction_details` | the note |

A transaction matches when it has the same day (in the phone's time zone), kind, amount (as a number, so `12.50`
matches `12.5`), account names ignoring case, and note as it would be written to the file. `date`, `kind` and
`amount` are required; the other three may be empty.

Transactions imported in the same run are tried first, then the ones already in the app. So importing a whole export
into an app that already holds the same data links the rows to the new copies, not the old ones.

## How an import runs

1. Every file is read and its type found from its header. If any file can't be read or has an unknown header, the
   import stops before anything is saved.
2. The files are imported in this order, whatever order they were picked in, so that rows can refer to what earlier
   files created: accounts, categories, transactions, planned transactions, budgets, collections, collection
   transactions, receipt items.
3. The app reports how many transactions were imported, how many accounts and categories were created, and which
   lines were skipped.

Android saves the whole import in one database transaction, so a failure saves nothing; iOS saves once at the end.

### How a file's type is found

The rules are tried in order, and the first one whose columns are all in the header decides:

| # | Columns | Type |
| --- | --- | --- |
| 1 | `date`, `amount`, `source`, `target` | transfers |
| 2 | `date`, `amount`, `destination`, `category` | incomes |
| 3 | `date`, `amount`, `source`, `category` | expenses |
| – | `date`, `amount`, anything else | unknown |
| 4 | `collection`, `transaction_date` | collection transactions |
| 5 | `transaction_date`, `name` | receipt items |
| 6 | `kind`, `amount`, `frequency`, `start_date` | planned transactions |
| 7 | `limit` | budgets |
| 8 | `name`, `initial_value` | accounts |
| 9 | `name`, `kind` | categories |
| 10 | `name`, `icon`, `color` | collections |
| – | anything else | unknown |

A file from another app imports when its header matches rule 1, 2 or 3, for example a bank export with
`date;amount;source;category;details`.

## Differences between the apps

- **Line numbers of skipped rows in Windows files.** In a file with `\r\n` line breaks, such as one saved by Excel,
  iOS counts every line break twice: a bad row on line 2 is reported as line 3, and one on line 3 as line 5.
  Android reports 2 and 3. The rows themselves are read the same way. iOS issue #20 has the fix.
- **Demo data icon.** `DemoData/generate.py` uses `desktopcomputer` for the Home Office collection, which isn't in
  `Icons.swift`. Neither app offers it, and Android shows a tag. Android's `DemoDataIconsTest` lists it as the only
  exception and fails once iOS changes it, as a reminder to copy the new `DemoData`.

## Known limits

Both apps share these today. They're listed so we can decide together whether any is worth a format change.

- Re-importing transactions duplicates them, because rows have no IDs.
- `;`, `"` and line breaks in names and notes don't survive a round trip.
- Times of day are lost; imported transactions are at 00:00.
- English-style amounts with thousands separators (`1,234.56`) are misread.
- Receipt photos aren't exported (iOS issue #15).
- An imported budget never updates an existing one (iOS issue #19).
