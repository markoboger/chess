---
marp: true
title: "Lecture 03: Import and Export"
description: "Parsers, Grammars, JSON, FEN, PGN, and Dependency Injection"
paginate: true
theme: htwg
---

<!-- _class: title -->
# Lecture 03
## Import and Export

<span class="eyebrow">Software Architecture with AI</span>

Parsers, grammars, JSON, FEN, PGN, and how to switch implementations cleanly

<p class="small">Prof. Dr. Marko Boger</p>

---

# Why Import and Export Matter

- software rarely lives alone
- users want to save, load, copy, paste, and exchange data
- systems need interfaces not only for humans, but also for other tools
- the same domain often needs multiple representations

For chess, this means:

- JSON for structured application data
- FEN for positions
- PGN for move history

---

# Representation vs Domain

The chess board in memory is one thing.
Its textual representations are something else.

We need to transform between:

- internal domain objects
- external textual formats
- files
- copy-paste into the UI

This transformation layer is where parsers live.

---

# What Is a Grammar?

A grammar describes the structure of a language.

It tells us:

- which strings are valid
- how tokens can be combined
- what nested structure a text can have

A parser then checks whether an input follows that grammar and, if so, builds a result.

---

# Language Classes

The classical Chomsky hierarchy:

- regular languages
- context-free languages
- context-sensitive languages
- recursively enumerable languages

For software engineering, the first two matter most in practice:

- regular
- context-free

---

# Regular Languages

Typical tool:

- regular expressions

Good for:

- simple patterns
- tokens
- lightweight validation

Examples:

- email-like patterns
- identifiers
- simple fixed formats

---

# Context-Free Languages

Typical tools:

- parser combinators
- parser generators
- recursive descent parsers

Good for:

- nested structure
- expressions
- languages with recursive rules

Examples:

- arithmetic expressions
- many programming languages
- structured configuration formats

---

# Why This Matters for Us

Different data formats have different complexity:

- JSON has nested structure
- FEN is compact and fairly regular
- PGN is more structured and more difficult

So parser choice is an engineering decision, not just a coding preference.

---

# External DSLs

An external DSL is a language outside the host programming language.

Examples:

- SQL
- Markdown
- YAML
- JSON
- FEN
- PGN

They are all text, but they have different grammars, expectations, and tooling.

---

# SQL as External DSL

```sql
SELECT eco, opening_name, games_count
FROM opening_stats
WHERE games_count > 50
ORDER BY games_count DESC;
```

Why it matters:

- declarative
- domain-specific
- not a general-purpose language
- parsed and executed by a database engine

---

# Markdown as External DSL

```md
# Title

- point one
- point two

`inline code`
```

Why it matters today:

- documentation
- LLM prompts and context files
- slide systems like Marp

---

# YAML as External DSL

```yaml
services:
  postgres:
    image: postgres:16
    ports:
      - "5432:5432"
```

Why it matters:

- configuration
- deployment
- CI pipelines
- Docker Compose

It is easy to read, but indentation makes parsing more delicate.

---

# JSON

JSON is special because it is:

- an external language
- simple enough to be universal
- directly usable in JavaScript

Example:

```json
{
  "gameId": "abc123",
  "fen": "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR"
}
```

---

# Why JSON Became So Important

- natural for web APIs
- supported by almost every language
- easy to inspect manually
- maps well to objects, arrays, numbers, booleans, and strings

But outside JavaScript, JSON must still be:

- encoded
- decoded
- validated
- mapped to types

---

# JSON in Scala

In Scala we cannot just say:

```scala
val game = someJson
```

We need a library to:

- build JSON
- parse JSON text
- decode JSON into Scala values
- encode Scala values into JSON

In this project, we use **Circe**.

---

# Circe

Circe is a Scala JSON library with:

- encoders
- decoders
- automatic and semi-automatic derivation
- parser support
- good integration with Scala case classes

It fits well into a typed functional style.

---

# Circe Example

```scala
case class MakeMoveRequest(move: String)

object MakeMoveRequest:
  given Decoder[MakeMoveRequest] = deriveDecoder
  given Encoder[MakeMoveRequest] = deriveEncoder
```

This gives us:

- JSON -> Scala
- Scala -> JSON

without writing the whole conversion by hand.

---

# Reading Circe Code

- case class defines the shape of the data
- `Decoder` tells Circe how to read JSON
- `Encoder` tells Circe how to write JSON
- derivation reduces boilerplate

This is a good example of how libraries automate repetitive parser work.

---

# Chess-Specific Formats

The chess application also needs two domain-specific languages:

- **FEN**
  Forsyth-Edwards Notation for a position
- **PGN**
  Portable Game Notation for move history

These are external DSLs for chess.

---

# FEN Example

```text
rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1
```

What it contains:

- piece placement
- side to move
- castling rights
- en passant field
- halfmove clock
- move number

Compact, standardized, very useful.

---

# PGN Example

```text
1. e4 e5 2. Nf3 Nc6 3. Bb5 a6
```

What it represents:

- ordered move sequence
- standard chess notation
- optionally metadata tags in full PGN files

PGN is more complex to parse than FEN.

---

# Parser Technologies in This Project

For FEN and PGN we use three approaches:

- regular-expression based parser
- parser combinators
- FastParse

This gives us something valuable for architecture:

- same interface
- multiple implementations
- tradeoff discussion

---

# Simple Regex Parser

Regex-based parsing can be a good fit when:

- the format is simple
- we want performance
- we want minimal dependencies

In this project:

- the regex FEN parser is fast
- but it is less pleasant to extend and maintain than structured parsers

---

# Why Regex Becomes Painful

- poor readability for complex structure
- hard to model recursion and nesting
- debugging becomes unpleasant
- one big pattern often hides intent

Regex is a useful tool.
It is not always a good architecture for parsing.

---

# Parser Combinators

Parser combinators build large parsers from small parsers.

The idea comes from:

- formal grammar thinking
- EBNF-like decomposition
- classic parser construction

In Scala, combinators let us write parsers almost like grammar rules.

---

# EBNF Connection

Example idea:

```text
rank   = square { square } ;
square = piece | digit ;
piece  = "p" | "n" | "b" | "r" | "q" | "k" | ...
```

Parser combinators often mirror that structure directly in code.

That is why they are good for teaching.

---

# Historical Roots

Parser combinators and parser tools grow out of a long tradition:

- grammar formalisms such as BNF and EBNF
- Unix tools such as `lex` and `yacc`
- compiler construction
- recursive descent parsing

So when we use combinators, we are standing on classic language-tooling ideas.

---

# Simplified Combinator Example

```scala
def piece: Parser[Char] =
  "[prnbqkPRNBQK]".r ^^ (_.charAt(0))

def empty: Parser[Int] =
  "[1-8]".r ^^ (_.toInt)
```

The code reads like grammar fragments.

Advantages:

- composable
- expressive
- maintainable

---

# FastParse

FastParse is another Scala parsing library.

Main idea:

- combinator style
- better performance
- explicit, lightweight parser definitions

So it tries to give us:

- the readability of structured parsing
- better runtime behavior than older combinator approaches

---

# Simplified FastParse Example

```scala
def file[$: P] = P(rank.rep(exactly = 8, sep = "/"))
def rank[$: P] = P(square.rep(min = 1))
def square[$: P] = P(piece | digit)
```

Again, the code resembles grammar.

That makes it much easier to explain than a giant regular expression.

---

# Tradeoffs Between the Three

| Approach | Strength | Weakness |
|---|---|---|
| Regex | fast, small, simple for small formats | poor maintainability |
| Combinators | readable, close to grammar | can be slower |
| FastParse | readable and performant | extra dependency, different style |

The right choice depends on:

- format complexity
- change frequency
- performance needs
- team readability

---

# Architecture Trick

We do not want the whole application to care which parser is used.

So we define abstractions such as:

```scala
trait FenIO
trait PgnIO
```

and hide the implementation behind them.

That is where dependency injection enters.

---

# Dependency Injection

Dependency injection means:

- code depends on abstractions
- wiring chooses the concrete implementation

Benefits:

- switching implementation is easy
- testing becomes easier
- architecture stays cleaner

---

# Google Guice

One common Java-style approach:

- container-based dependency injection
- bindings configured in modules
- objects created through the injector

Advantages:

- powerful
- familiar in enterprise software

Disadvantages:

- more framework machinery
- more runtime indirection

---

# Scala 3: `given` / `using`

Scala 3 offers a lighter way:

```scala
class GameController(using FenIO, PgnIO)
```

and in the wiring:

```scala
given FenIO = RegexFenParser
given PgnIO = PgnFileIO()
```

This is compile-time wiring instead of a big runtime container.

---

# From the Chess Project

In [AppBindings.scala](/Users/markoboger/workspace/chess/src/main/scala/chess/AppBindings.scala):

```scala
given FenIO = RegexFenParser
// given FenIO = CombinatorFenParser
// given FenIO = FastParseFenParser

given PgnIO = PgnFileIO()
// given PgnIO = CombinatorPgnParser
// given PgnIO = FastParsePgnParser
```

One line changes the active implementation.

---

# Why This Is Architecturally Nice

- parser choice stays in one place
- UI code does not care
- controller code does not care
- we can benchmark alternatives later
- students can compare implementations cleanly

This is a good example of dependency inversion in practice.

---

# Import and Export in the UI

The user should be able to:

- see FEN
- see PGN
- copy and paste FEN
- copy and paste PGN
- export JSON to a file
- import JSON from a file

So parsing is not abstract.
It is visible as a real user feature.

---

# Suggested UI Behavior

- a FEN field showing the current position
- a PGN field showing move history
- buttons for:
  - import JSON
  - export JSON
  - load FEN
  - load PGN
  - copy FEN
  - copy PGN

This ties the parser lecture back to user value.

---

# Assignment

## Extend the chess application with import and export

Students must implement:

1. JSON import and export for chess data
2. file save and file load from the UI
3. a FEN parser with:
   - regex
   - parser combinators
   - FastParse
4. a PGN parser with:
   - regex or hand-written parser
   - parser combinators
   - FastParse
5. FEN and PGN display in the UI
6. copy-paste support using these parsers
7. dependency injection to choose the active parser implementation

---

# Deliverables

- working JSON import and export
- working FEN import and export
- working PGN import and export
- three FEN parser implementations
- three PGN parser implementations
- DI-based parser selection
- a short explanation of tradeoffs between the parser technologies

Optional:

- benchmark the parser variants

---

# Key Message

Import and export are not just file handling.

They are about:

- language design
- grammar
- parser technology
- data transformation
- and clean architectural separation

The chess application becomes much more useful once it can speak multiple languages.
