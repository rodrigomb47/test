"""Whitelist expression parser for part parameters and base dimensions (no eval).

Grammar:  expr := term (('+' | '-') term)* ;  term := unary (('*' | '/') unary)* ;
          unary := ('+' | '-') unary | atom ;  atom := number | name | name '(' expr (',' expr)* ')' | '(' expr ')'
Names come from the environment (part params, base dimensions); functions are min, max, abs, floor, ceil, round
(half away from zero) and clamp(x, lo, hi).
"""
import math
import re

from util import HerogenError

_TOKEN = re.compile(r"\s*(?:(\d+\.\d*|\.\d+|\d+)|([A-Za-z_][A-Za-z0-9_]*)|(.))")


def _round_half_up(x):
    return math.floor(x + 0.5) if x >= 0 else -math.floor(-x + 0.5)


FUNCS = {
    "min": (2, None, min),
    "max": (2, None, max),
    "abs": (1, 1, abs),
    "floor": (1, 1, math.floor),
    "ceil": (1, 1, math.ceil),
    "round": (1, 1, _round_half_up),
    "clamp": (3, 3, lambda x, lo, hi: max(lo, min(hi, x))),
}


def _tokens(text):
    out = []
    pos = 0
    text = text.strip()
    while pos < len(text):
        m = _TOKEN.match(text, pos)
        if not m or m.end() == pos:
            break
        number, name, op = m.groups()
        if number is not None:
            out.append(("num", float(number)))
        elif name is not None:
            out.append(("name", name))
        elif op is not None:
            if op not in "+-*/(),":
                raise HerogenError("expression %r: character %r is not allowed" % (text, op))
            out.append(("op", op))
        pos = m.end()
    out.append(("end", None))
    return out


class _Parser:
    def __init__(self, text, env):
        self.text = text
        self.env = env
        self.toks = _tokens(text)
        self.i = 0

    def fail(self, msg):
        raise HerogenError("expression %r: %s" % (self.text, msg))

    def peek(self):
        return self.toks[self.i]

    def take(self):
        t = self.toks[self.i]
        self.i += 1
        return t

    def expect(self, op):
        t = self.take()
        if t != ("op", op):
            self.fail("expected %r" % op)

    def parse(self):
        v = self.expr()
        if self.peek()[0] != "end":
            self.fail("unexpected %r" % (self.peek()[1],))
        return v

    def expr(self):
        v = self.term()
        while self.peek() in (("op", "+"), ("op", "-")):
            if self.take()[1] == "+":
                v = v + self.term()
            else:
                v = v - self.term()
        return v

    def term(self):
        v = self.unary()
        while self.peek() in (("op", "*"), ("op", "/")):
            if self.take()[1] == "*":
                v = v * self.unary()
            else:
                d = self.unary()
                if d == 0:
                    self.fail("division by zero")
                v = v / d
        return v

    def unary(self):
        if self.peek() == ("op", "-"):
            self.take()
            return -self.unary()
        if self.peek() == ("op", "+"):
            self.take()
            return self.unary()
        return self.atom()

    def atom(self):
        kind, val = self.take()
        if kind == "num":
            return val
        if kind == "name":
            if self.peek() == ("op", "("):
                if val not in FUNCS:
                    self.fail("unknown function %r" % val)
                self.take()
                args = [self.expr()]
                while self.peek() == ("op", ","):
                    self.take()
                    args.append(self.expr())
                self.expect(")")
                lo, hi, fn = FUNCS[val]
                if len(args) < lo or (hi is not None and len(args) > hi):
                    self.fail("%s() takes %s arguments" % (val, lo if lo == hi else "%d or more" % lo))
                return float(fn(*args))
            if val not in self.env:
                self.fail("unknown name %r" % val)
            return float(self.env[val])
        if (kind, val) == ("op", "("):
            v = self.expr()
            self.expect(")")
            return v
        self.fail("unexpected %r" % (val,))


def evaluate(value, env):
    """A number, or an expression string, or a list of either."""
    if isinstance(value, bool):
        raise HerogenError("expected a number, got %r" % (value,))
    if isinstance(value, (int, float)):
        return float(value)
    if isinstance(value, str):
        return _Parser(value, env).parse()
    if isinstance(value, (list, tuple)):
        return [evaluate(v, env) for v in value]
    raise HerogenError("expected a number or an expression, got %r" % (value,))
