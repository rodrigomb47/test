import unittest

from helpers import HEROGEN  # noqa: F401  (sys.path)

from expr import evaluate
from util import HerogenError


class ExprTest(unittest.TestCase):
    def test_numbers_and_precedence(self):
        self.assertEqual(evaluate(3, {}), 3.0)
        self.assertEqual(evaluate("2 + 3 * 4", {}), 14.0)
        self.assertEqual(evaluate("(2 + 3) * 4", {}), 20.0)
        self.assertEqual(evaluate("-2 - -3", {}), 1.0)
        self.assertEqual(evaluate("10 / 4", {}), 2.5)
        self.assertEqual(evaluate(".5 + 1.", {}), 1.5)

    def test_names_and_lists(self):
        env = {"length": 10, "chest_w": 7}
        self.assertEqual(evaluate("4 + length * 0.45", env), 8.5)
        self.assertEqual(evaluate(["-chest_w / 2", 18, "length"], env), [-3.5, 18.0, 10.0])

    def test_functions(self):
        self.assertEqual(evaluate("min(3, 1, 2)", {}), 1.0)
        self.assertEqual(evaluate("max(3, 1)", {}), 3.0)
        self.assertEqual(evaluate("abs(-2)", {}), 2.0)
        self.assertEqual(evaluate("round(2.5)", {}), 3.0)       # half up, unlike Python's round
        self.assertEqual(evaluate("round(-2.5)", {}), -3.0)
        self.assertEqual(evaluate("floor(2.7) + ceil(2.1)", {}), 5.0)
        self.assertEqual(evaluate("clamp(12, 0, 10)", {}), 10.0)

    def test_rejects_everything_else(self):
        for bad in ("__import__('os')", "a.b", "length ** 2", "1 +", "(1", "foo(1)", "unknown", "1 / 0", "1 2",
                    "abs(1, 2)", "clamp(1, 2)"):
            with self.subTest(bad=bad), self.assertRaises(HerogenError):
                evaluate(bad, {"length": 2, "a": 1})
        with self.assertRaises(HerogenError):
            evaluate(True, {})


if __name__ == "__main__":
    unittest.main()
