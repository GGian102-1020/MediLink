"""Exact nearest-rank p95 and non-empty sampling contract."""
import unittest
from scripts.medir_p95 import p95

class PerformanceContractTests(unittest.TestCase):
    def test_p95_nearest_rank(self):
        self.assertEqual(p95(list(range(1, 101))), 95)
        self.assertEqual(p95([.2] * 18 + [.3]), .3)

    def test_refuses_no_samples(self):
        with self.assertRaises(ValueError):
            p95([])

if __name__ == '__main__':
    unittest.main()
