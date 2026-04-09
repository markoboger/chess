---
marp: true
theme: default
paginate: true
_paginate: false
footer: "![](htwgin40.png)&nbsp;&nbsp;Name - Titel"
_footer : ""
---

<!-- Template created by Johannes Schneider and published at https://git.in.htwg-konstanz.de/schneider/htwg-in-marp-template. Freely usable by anyone with access to this repo. Please leave this comment in your presentation. -->

<!-- disabling some linting rules that do not work well for Marp presentations -->
<!-- markdownlint-disable MD001 MD024 MD033 MD041 MD045 -->

<style>
:root {
  --fgColor-accent: #009b91;
}
/* h1 gets accent color and strong parts in bold font */
h1 {
  color: var(--fgColor-accent);
}
h1 strong {
  font-weight: bold;
  color: var(--fgColor-accent);
}
/* h2-h5 gets default (= black) color with strong parts in accent color */
h2 strong, h3 strong, h4 strong, h5 strong {
  color: var(--fgColor-accent);
}
s { /* redefining ~~text~~ as bold in accent color */
    color: var(--fgColor-accent);
    text-decoration: none;
    font-weight: bold;
}
section {
  font-size: 24px; /* default is 29px */
  padding-top: 50px; /* a little less blank space above heading */
}
.columns {
  display: grid;
  grid-auto-flow: column;
  gap: 2em; /* Adds space between columns */
  grid-template-columns: repeat(2, 1fr);
}
</style>

![bg](htwgin-titel.png)

## Anlass des Vortrags<!-- omit in toc -->

# Titel des Vortrags <!-- omit in toc -->

Name

---

# Zwischenfolie

---

## Folientitel

- Das ist der erste Bulletpoint dieser Folie. Hier steht etwas. Und noch etwas. Und dann ist dieser Punkt auch schon zu Ende.
- Bullet point 2 mit ~~farblicher Hervorhebung~~.

> **Zitat**: Hier könnte Ihre Werbung stehen

---

<!-- 40% = Spaltenbreite, w = width in Pixel (geht auch mit h = height) -->
![bg right:40% w:400](https://placehold.net/5-600x800.png)

## Folie mit Bild rechts

1. **Das**: ist Punk1
2. **Hier** steht noch was
3. **und** auch noch ein Punkt

---

## Code-Blöcke

Kann man auch gut einbetten:

```java
System.out.println("Hello World");
```

---

## Mathematische Formeln

...werden auch unterstützt:

$$a^2 + b^2 = c^2$$

Geht auch direkt im Text: $x = \frac{17}{42}$.

---

## Zwei-spaltiges Layout

<div class="columns">

Wir eigentlich nicht direkt unterstützt, kann man sich aber mit CSS selbst bauen, siehe Beispiel hier bzw. am Anfgang der Präsentation die Styles. Jeder Absatz landet automatisch in einer Extraspalte. Wenn man mehr Kontrolle braucht, fügt man noch divs ein: `<div class="columns"><div>Column1</div><div>Column2</div></div>`

```java
public void mergeSort(int[] a, int n) {
  if (n < 2) {
    return;
  }
  int mid = n / 2;
  int[] l = new int[mid];
  int[] r = new int[n - mid];

  for (int i = 0; i < mid; i++) {
    l[i] = a[i];
  }
  for (int i = mid; i < n; i++) {
    r[i - mid] = a[i];
  }
  mergeSort(l, mid);
  mergeSort(r, n - mid);

  merge(a, l, r, mid, n - mid);
}
```

</div>

---

## Bearbeitbare Graphiken einbetten

![h:400](diagram.drawio.svg)

Bild kann mit Draw.io-Plugin direkt in VS Code bearbeitet werden.

Zum Update Vorschau schließen und wieder öffnen.
