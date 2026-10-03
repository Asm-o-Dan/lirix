"""Execute the checked-in Room SQL and migration without an Android runtime.

Run: python3 -m unittest discover -s core/storage/src/test/python -v
"""
import json
from pathlib import Path
import re
import sqlite3
import unittest

STORAGE = Path(__file__).resolve().parents[3]
MAIN = STORAGE / "src/main/kotlin/com/example/npc/core/storage"
SCHEMA = STORAGE / "schemas/com.example.npc.core.storage.AppDatabase/5.json"
DAO_SOURCE = (MAIN / "dao/FinancialTransactionDao.kt").read_text()
QUERIES = {
    name: sql
    for sql, name in re.findall(
        r'@Query\("""(.*?)"""\)\s*(?:suspend\s+)?fun\s+(\w+)',
        DAO_SOURCE,
        re.DOTALL,
    )
}
MIGRATION_STATEMENTS = re.findall(
    r'db\.execSQL\("([^"]+)"\)',
    (MAIN / "migration/MIGRATION_5_6.kt").read_text(),
)


class TransactionSqlTest(unittest.TestCase):
    def setUp(self):
        self.db = sqlite3.connect(":memory:")
        self.db.row_factory = sqlite3.Row
        for entity in json.loads(SCHEMA.read_text())["database"]["entities"]:
            self.db.execute(entity["createSql"].replace("${TABLE_NAME}", entity["tableName"]))
            for index in entity["indices"]:
                self.db.execute(index["createSql"].replace("${TABLE_NAME}", entity["tableName"]))

    def tearDown(self):
        self.db.close()

    def migrate(self):
        self.assertEqual(len(MIGRATION_STATEMENTS), 2)
        for statement in MIGRATION_STATEMENTS:
            self.db.execute(statement)

    def insert(self, direction="DEBIT", amount=100, status="COMPLETED", **extra):
        fields = dict(
            bank="AnyBank", direction=direction, amount_minor=amount, currency="USD",
            occurred_at=1000, extractor_id="test", extractor_version=1, created_at=1000,
        )
        if "status" in {row[1] for row in self.db.execute("PRAGMA table_info(financial_transaction)")}:
            fields["status"] = status
        fields.update(extra)
        keys = ",".join(fields)
        placeholders = ",".join("?" for _ in fields)
        return self.db.execute(
            f"INSERT INTO financial_transaction ({keys}) VALUES ({placeholders})",
            list(fields.values()),
        ).lastrowid

    def query(self, method, **params):
        return [dict(row) for row in self.db.execute(QUERIES[method], params)]

    def test_migration_preserves_all_legacy_fields_and_indexes(self):
        identity = self.insert(amount=4321, merchant="My merchant", isRefund=1, bankVersion=7)
        before = dict(self.db.execute("SELECT * FROM financial_transaction").fetchone())
        indexes = self.db.execute("PRAGMA index_list(financial_transaction)").fetchall()
        self.migrate()
        after = dict(self.db.execute("SELECT * FROM financial_transaction").fetchone())
        self.assertEqual({key: after[key] for key in before}, before)
        self.assertEqual(after["id"], identity)
        self.assertEqual(after["status"], "COMPLETED")
        self.assertEqual(after["tx_status"], "CONFIRMED_AUTO")
        self.assertEqual(self.db.execute("PRAGMA index_list(financial_transaction)").fetchall(), indexes)

    def test_migration_adds_only_status_columns_and_preserves_constraints(self):
        columns_before = [tuple(row) for row in self.db.execute("PRAGMA table_info(financial_transaction)")]
        constraints_before = {
            pragma: [tuple(row) for row in self.db.execute(f"PRAGMA {pragma}(financial_transaction)")]
            for pragma in ("foreign_key_list", "index_list")
        }
        self.migrate()
        columns_after = [tuple(row) for row in self.db.execute("PRAGMA table_info(financial_transaction)")]
        self.assertEqual(columns_after[:len(columns_before)], columns_before)
        self.assertEqual([row[1:] for row in columns_after[len(columns_before):]], [
            ("status", "TEXT", 1, "'COMPLETED'", 0),
            ("tx_status", "TEXT", 1, "'CONFIRMED_AUTO'", 0),
        ])
        for pragma, expected in constraints_before.items():
            self.assertEqual(
                [tuple(row) for row in self.db.execute(f"PRAGMA {pragma}(financial_transaction)")],
                expected,
            )

    def test_totals_exclude_unknown_invalid_and_declined_rows(self):
        self.migrate()
        self.insert("DEBIT", 100)
        self.insert("CREDIT", 200)
        self.insert("DEBIT", 900, "DECLINED")
        self.insert("CREDIT", 1000, "DECLINED")
        self.insert("UNKNOWN", 9999, isRefund=1)
        self.insert("garbled", 8888)
        rows = self.query("observeAggregatedTotalsByCurrency", fromEpochMs=0, toEpochMs=2000, direction=None)
        self.assertEqual(rows, [dict(currency="USD", expenseMinor=100, incomeMinor=200, refundMinor=0, txCount=2)])
        rows = self.query("getAggregatedTotalsByCurrency", fromEpochMs=0, toEpochMs=2000, direction="DEBIT")
        self.assertEqual(rows, [dict(currency="USD", totalMinor=100, transactionCount=1)])
        self.assertEqual(self.query("getAggregatedTotalsByCurrency", fromEpochMs=0, toEpochMs=2000, direction="UNKNOWN"), [])

    def test_refunds_are_separate_from_income_and_declined_refunds_are_excluded(self):
        self.migrate()
        self.insert("CREDIT", 100, isRefund=1)
        self.insert("CREDIT", 200)
        self.insert("CREDIT", 900, "DECLINED", isRefund=1)
        rows = self.query("observeAggregatedTotalsByCurrency", fromEpochMs=0, toEpochMs=2000, direction=None)
        self.assertEqual(rows, [dict(currency="USD", expenseMinor=0, incomeMinor=200, refundMinor=100, txCount=2)])

    def test_declined_attempt_does_not_hide_or_delete_completed_payment(self):
        self.migrate()
        declined = self.insert("DEBIT", 100, "DECLINED")
        completed = self.insert("DEBIT", 100)
        rows = self.query("observeLatest", limit=10)
        self.assertEqual({row["id"] for row in rows}, {declined, completed})
        rows = self.query("observeAggregatedTotalsByCurrency", fromEpochMs=0, toEpochMs=2000, direction=None)
        self.assertEqual(rows, [dict(currency="USD", expenseMinor=100, incomeMinor=0, refundMinor=0, txCount=1)])
        self.db.execute(QUERIES["deleteDuplicates"])
        self.assertEqual(self.db.execute("SELECT COUNT(*) FROM financial_transaction").fetchone()[0], 2)

    def test_unresolved_equal_amounts_remain_separate_for_review(self):
        self.migrate()
        first = self.insert("UNKNOWN", 100)
        second = self.insert("UNKNOWN", 100)
        rows = self.query("observeLatest", limit=10)
        self.assertEqual({row["id"] for row in rows}, {first, second})
        self.db.execute(QUERIES["deleteDuplicates"])
        self.assertEqual(self.db.execute("SELECT COUNT(*) FROM financial_transaction").fetchone()[0], 2)


if __name__ == "__main__":
    unittest.main()
