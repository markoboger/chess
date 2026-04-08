---
marp: true
title: "Lecture 01: Monolith"
description: "AI-Assisted Software Architecture with Chess"
paginate: true
theme: htwg
---

<!-- _class: title -->
# Lecture 01
## Monolith as Architectural Baseline

<span class="eyebrow">Software Architecture with AI</span>

Chess as our running example, AI as our implementation accelerator.

<p class="small">Prof. Dr. Marko Boger</p>

---

# Why We Start With a Monolith

- one deployable
- one process
- one codebase
- one debugger
- one place to understand the rules of the system

**Before we distribute a system, we need to understand its core responsibilities.**

---

# Course Positioning

## This is not a course about typing code faster

- AI will help us generate code, tests, documentation, and refactorings
- you are still responsible for architecture, quality, and correctness
- the more AI helps with implementation, the more important good boundaries become

> AI changes the workflow. It does not remove the need for software design.

---

# The Running System

We use a chess application because it has:

- clear domain rules
- meaningful state transitions
- multiple UIs
- import and export formats
- natural service boundaries later
- realistic persistence and analytics use cases

---

# Lecture 01 Goals

By the end of today, students should be able to:

- explain what a monolith is
- describe a layered architecture
- identify stable vs unstable responsibilities
- use AI to explore and extend an existing codebase
- verify AI-generated changes instead of trusting them blindly

---

# Baseline Architecture

```text
┌──────────────────────────────┐
│ View                         │
│ GUI, Web UI, Console         │
├──────────────────────────────┤
│ Application / Controller     │
│ Game flow, history, commands │
├──────────────────────────────┤
│ Domain Model                 │
│ Board, pieces, move rules    │
└──────────────────────────────┘
```

- dependencies flow downward
- domain logic stays independent
- UI does not contain chess rules

---

# In This Repo

Today we use the monolithic view of the codebase even though the project evolves later.

- `core/` contains pure chess logic and notation
- `app/` contains application logic and strategies
- `src/` contains runnable entry points and UI wiring

For Lecture 01, we deliberately read this as **one system with clear internal layers**.

---

# What Makes a Good Monolith?

- cohesive domain logic
- explicit boundaries inside the process
- low ceremony
- easy local development
- simple deployment
- fast feedback for experiments

**A monolith is not "bad architecture". A monolith can be very well-structured.**

---

# Where AI Fits on Day 1

AI is useful for:

- reading unfamiliar code
- generating tests
- proposing refactorings
- scaffolding small features
- explaining tradeoffs

AI is dangerous when:

- we accept plausible but wrong chess logic
- we let it collapse boundaries
- we skip verification

---

# New Development Loop

## Classical Loop

1. understand problem
2. design
3. implement manually
4. test

## AI-Assisted Loop

1. understand problem
2. define boundaries and acceptance criteria
3. prompt AI with context and constraints
4. inspect generated code critically
5. run tests and validate behavior

---

# Good Prompting for Architecture

Weak prompt:

```text
Build a chess app.
```

Better prompt:

```text
Add a feature to the monolithic chess app.
Keep move validation in the domain layer.
Do not put business logic into the UI.
Add tests for the new behavior.
```

---

# Architectural Responsibility

When using AI, the human still decides:

- where code belongs
- what dependencies are allowed
- what "done" means
- how to validate correctness
- when a generated solution should be rejected

---

# What We Will Inspect Live

- where move validation lives
- where state is stored
- how the UI learns about changes
- how import and export are separated from domain logic

This gives us the baseline for all later lectures.

---

# Typical Mistakes Students Make

- putting logic into the UI because it is "quick"
- treating generated code as ground truth
- mixing parsing, domain logic, and persistence
- optimizing too early
- decomposing into microservices before understanding the monolith

---

# Suggested Live Demo

1. run the chess application
2. trace a move through the system
3. ask AI to explain the architecture
4. ask AI to add a tiny feature
5. inspect whether the feature respects layering
6. run tests before accepting the change

---

# First Evaluation Rubric

Students should be able to answer:

- what is the core domain object?
- what code is pure and testable?
- which parts are stateful?
- what should never depend on the UI?
- how would you ask AI to preserve the architecture?

---

# Exercise

Use AI to answer these questions about the repo:

1. Which classes belong to the domain core?
2. Where is mutable or session state held?
3. Which dependency direction would be an architectural violation?
4. What test would you add first to verify a move rule?

Then verify the answers in the actual code.

---

# Quality From Day One

Even in Lecture 01, the project should start with a clean engineering setup:

- automated build on every push
- automated test execution
- coverage reporting
- static quality analysis

Why so early?

- AI can generate code quickly
- quality tooling tells us when it generated nonsense quickly

---

# Tooling Reminder

For this project, students should set up:

- **GitHub Actions**
  to run build and tests automatically
- **Coveralls**
  to visualize code coverage over time
- **SonarQube**
  to inspect code quality, smells, and maintainability risks

These tools do not replace tests.
They reinforce discipline.

---

# Suggested Pipeline

```text
git push / pull request
        │
        ▼
GitHub Actions
        │
        ├── sbt compile
        ├── sbt test
        ├── scoverage report
        ├── Coveralls upload
        └── SonarQube scan
```

This is one of the earliest examples of architecture beyond code.

---

# Why Students Need This Reminder

- teams forget CI until it hurts
- coverage is easy to postpone
- SonarQube is valuable only if it starts early
- project hygiene is part of architecture, not admin work

The earlier the feedback loop starts, the more useful it is.

---

# Lecture 01 Task Extension

## Set up project quality automation

Each team should:

1. create a GitHub Actions workflow for build and test
2. enable `scoverage`
3. connect the repository to Coveralls
4. configure a SonarQube scan
5. verify that one push produces all reports successfully

---

# Deliverables for the Setup Task

- one GitHub Actions workflow file
- one successful CI run
- coverage visible in Coveralls
- SonarQube scan visible for the repository
- a short note describing:
  - which command CI runs
  - where the secrets are configured
  - which failures should block a merge

---

# Key Message

## We are not replacing software architecture with AI

We are using AI to:

- move faster
- explore more alternatives
- spend more time on design and evaluation

The monolith is our control group for the rest of the course.

---

# Next Time

## Functional style and monadic thinking

- `Option`
- `Try`
- domain-safe error handling
- making state transitions more composable
- using AI to propose abstractions without losing clarity

---
