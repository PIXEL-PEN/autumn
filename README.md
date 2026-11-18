# Autumn

*The palest ink is better than the best memory.* — Chinese proverb

### You remember what happened.<br>Autumn remembers when.

A journalling and archiving app for Android, built for one particular way memory fails.

Most people keep the events and lose the order. You know it was a good year and you could list most of what was in it, but not what came before what — and given long enough the whole thing settles into an undifferentiated *back then*. Autumn is not a planner and not a reminder app. It takes a titled note, files it under the day it belongs to, and gives the sequence back.

**Status:** 1.0.1. In daily use, still young.

**Install:** from [Releases](https://github.com/PIXEL-PEN/autumn/releases), or F-Droid once it is listed there.

## Why

Folders are good at hierarchy and bad at time. Autumn keeps the sense of *when* that makes an archive worth having:

* **Fast capture.** A date, a category, a title. The title often carries the whole entry — they run to 252 characters, so a list of them reads as a journal without opening anything.
* **Creation order, not edit order.** An entry written in March stays in March, however often you revise it. This is the thing ordinary note apps get wrong.
* **Local and offline.** Plain Markdown files in a folder you own. No account, no sync service, no lock-in.

## Strictly offline

Autumn holds no `INTERNET` permission. Not "doesn't phone home" — *cannot*. Every outbound link was removed from the app, the renderer assets are bundled rather than fetched, and the build ships one language rather than pretending to thirty.

## How it stores things

```
/Documents/Autumn/
  2026/
    11_November/
      Travel/
        A long walk up Cat Bells.md
  Quicknotes/
    2026/
      2026-11 November.md
  Images/2026/     Files/2026/
```

One home folder, set in exactly one place. Year and month folder names are pinned to English so they stay stable if you change your phone's language.

Each note begins with a date stamp as its first line. That stamp is the durable record of when the entry was created — it survives rename, move, copy, reinstall and moving to a new phone, because it lives inside the file rather than in an index beside it.

## The screens

**Calendar** — a single day card showing the date you are filing against; tap it to choose another. Below it, a category and a title. The **+** writes the note and opens it; **Browse** jumps to that category in the tree.

**Tree** — Year → Month → Category → Note, newest first. Long-press to rename, move or delete. Files that are not journal entries appear under *Other files*, apart from the journal years.

**Year** — a year on one page: open-ended highlights, a personal-growth section, then every month's entries.

**Quicknote** — the yellow bolt, off until you switch it on in Settings → General. Trivia that does not deserve a file of its own: the shop, the salesperson, the thing you want next week and never again. A month's worth share one document, newest first, no category. Long-press to read the month without writing. A home-screen widget does the same, with or without the bolt.

## The Timeline

The reason for the rest of it.

The journal is written as things happen. The Timeline is put together from memory: a life one year at a time, going back as far as you care to. It is a project of its own, kept apart from the journal, and nothing flows into it by itself.

Each year gets a highlight, the one line it is remembered by, and lines for what happened, placed in their months. Older years are reconstructed from photographs, from the drawer of things you kept, from relatives who remember differently. One phrase against a year is often enough to bring it back; the rest arrives slowly, out of order, over several rainy afternoons. The years you keep a journal for are the easy ones: the detail is already written down.

The whole Timeline exports as one scrolling web page, pictures and all, so it opens on any computer without the app, prints, and keeps.

## Contextual views

What a thousand other journalling apps do is store what you wrote. The difference here is being able to ask the archive a question and get a shape back rather than a list.

Filter a year to one category and scroll: which months were busy, which were empty, where things clustered. For a health category that is every appointment, clinic visit and pharmacy trip in order, with the dense months visible from the top of the screen and the actual detail — the drug, the dose, what was said — one tap away.

That depends entirely on categories being entered faithfully. It is the only discipline Autumn asks of you, and it is where the value comes from.

## Other things it does

* **Search** across the whole archive. `blue fridge` wants both words anywhere in a note, `"blue fridge"` wants the phrase, `/fridges?/` is a regular expression. Results show the matching line and open the note at it.
* **Pictures** in one place, organised by year, whichever screen you add them from. Tap any thumbnail for full screen, swipe between them.
* **Import** text and Markdown files into a new note: Share → Autumn, Open with → Autumn, or Import file in the overflow menu.
* **Backup** the whole archive to a single zip, anywhere, and **export** it as web pages for reading on a computer.
* **Guide** in the overflow menu.

## Building

Requires Android Studio and the Android SDK. There are no product flavours; the variants are `debug` and `release`.

```bash
git clone https://github.com/PIXEL-PEN/autumn.git
cd autumn
./gradlew installDebug
```

Debug builds install under their own application id and read `/Documents/Autumn (dev)`, so a development build cannot touch a real archive.

## Licence

**GPL-3.0.** Copyright (C) 2026 PIXEL-PEN. See `LICENSE.txt`.

Autumn is a derivative work of [Markor](https://github.com/gsantner/markor), Copyright 2017-2025 Gregor Santner, licensed under Apache 2.0 — which permits redistribution under the GPL. Markor's notice is retained in `LICENSE.txt` and its licence text, with every third-party licence the app depends on, is reachable in the app under Settings → About.

Autumn is not affiliated with or endorsed by Markor. Please report issues with Autumn here, not upstream, or write to autumn@spokenfor.ca.

## Acknowledgments

Built on Markor and its community.

The Tree screen owes its existence to [@harshad1](https://github.com/harshad1)'s early standalone HTML calendar prototype, raised in [this discussion](https://github.com/gsantner/markor/discussions/2278). It inspired a more developed version built around categories, which became an integration into Markor through a WebView, and eventually the architecture the Tree is built on today. The road was long, but the idea was his.
