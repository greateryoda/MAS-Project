# Résultats des expériences — 13/11/2026

Conditions :
- **LLM** : `google/gemma-4-e4b` via LM Studio (port 1235), parallélisme 4. Il sert à la fois à rédiger, à arbitrer le routage et à juger.
- **Embeddings** : `text-embedding-nomic-embed-text-v1.5`.
- **Jeu de données** : `eval/dataset.json`, 30 emails étiquetés, dont 14 marqués « reply ».
- **Rapports bruts** (non versionnés) : `eval/results/routing-20261113-114643.{md,csv}` et `eval/results/replies-20261113-125309.{md,json}`.

## 1. Routage (30 emails)

Commande : `java -cp "bin;lib/*" Evaluation routing`

| Configuration | Exactitude stricte | Exactitude tolérante | Appels LLM |
|---|---|---|---|
| full (règles + arbitre LLM) | 87 % | 93 % | 15 |
| rules-only (sans arbitre) | 63 % | 63 % | 0 |
| llm-only (arbitre seul) | 87 % | 97 % | 30 |
| no-keywords | 80 % | 90 % | — |
| content-only (sans contact ni fil) | 90 % | 97 % | — |

« Tolérante » accepte aussi un second agent jugé acceptable pour l'email.

Erreurs de la configuration `full` :
- **T05** (cross-context). Lucas, un contact connu du rôle recherche, écrit au sujet d'un cours, et l'email part vers RESEARCHPROJECT. Le vote ContactStep (1.0) domine, si bien que l'arbitre n'est jamais consulté.
- **A04** (« Rapport d'activité annuel », en français). L'email part vers TEACHER dans toutes les configurations, car il manque des mots-clés administratifs français.
- La configuration `no-keywords` se trompe aussi sur **R04**.

Lecture :
- L'arbitre LLM apporte +24 points par rapport aux règles seules.
- `full` atteint la même exactitude stricte que `llm-only` avec moitié moins d'appels LLM.
- Le signal « contact » nuit quand le contenu contredit le rôle habituel de l'expéditeur (T05).

## 2. Qualité des réponses (6 emails : S01, S02, S03, T01, T03, T05)

Commande : `java -cp "bin;lib/*" Evaluation replies --limit 6`

| Configuration | Score moyen /10 | Approuvés (≥7) | Contexte (car.) | Prompt (car.) | Temps/brouillon | Non jugés |
|---|---|---|---|---|---|---|
| multi-agent (sélection lexicale) | 7.83 | 83 % | 330 | 1 523 | 49 s | 0 |
| multi-agent, tout le contexte | 8.00 | 80 % | 731 | 2 058 | 50 s | 1 |
| oracle-routing (bon agent imposé) | **8.83** | **100 %** | 243 | 1 361 | 43 s | 0 |
| single-agent (baseline) | 8.25 | 100 % | 3 733 | 6 102 | 62 s | 2 |

« Non jugés » : la réponse du juge était illisible. Ces brouillons sont exclus des moyennes, ce qui favorise un peu l'agent unique.

Scores par email :

| Email | Catégorie | multi-agent | tout contexte | oracle | single-agent |
|---|---|---|---|---|---|
| S01 TP3 deadline | clear | 9 | 10 | 9 | 9 |
| S02 Demande de prolongation (FR) | clear | 9 | 10 | 9 | – |
| S03 My grade for TP1 | unknown-sender | 9 | – | 9 | 8 |
| T01 Seminar talk | clear | 9 | 9 | 9 | 8 |
| T03 Co-supervising an internship | unknown-sender | 8 | 9 | 8 | – |
| T05 Replacing you for Software Architecture | cross-context | 3 (mal routé) | 2 (mal routé) | 9 | 8 |

Lecture :
- **Avec le bon agent, le multi-agent fait mieux que l'agent unique** : 8.83 contre 8.25, avec un prompt 4,5 fois plus court et des réponses environ 30 % plus rapides.
- **L'écart du multi-agent complet vient d'une seule erreur de routage, T05.** Sans T05, le multi-agent est à 8.8.
- **La sélection du contexte ne dégrade pas la qualité** (7.83 contre 8.00, différence non significative sur 6 emails) et divise le contexte par 2,2.

## 3. Sélection par embeddings et sélection lexicale (sondage qualitatif)

- La sélection par embeddings fonctionne : 100 à 800 ms avec cache, environ 10 s à froid.
- Elle est moins pertinente que BM25 sur les emails en français (A04, R05).
- Les préfixes `search_query:` / `search_document:` attendus par nomic ne sont pas encore utilisés.

## 4. Observations qualitatives et limites

- **Langue** : pour S02 (en français), le multi-agent, l'oracle et l'agent unique répondent en français. La configuration tout-contexte répond en anglais, et le juge lui donne quand même 10.
- **Placeholders** : certains brouillons contiennent encore des trous (`[Your Name/The Lecturer]`, `[Indiquer ici où le document doit être remis…]`), notés 9 ou 10 malgré tout.
- **Juge indulgent et biaisé** : c'est le même petit modèle qui rédige et qui juge. Pour la suite, il faut un juge plus gros via `--judge-model` et une relecture humaine d'un échantillon.
- **Échantillon réduit** : 6 emails pour les réponses. Les tendances doivent être confirmées sur les 14 emails « reply ».

## 5. Pistes d'amélioration (non appliquées)

1. Baisser le poids de ContactStep à environ 0.6, pour qu'un contenu contradictoire déclenche l'arbitre (corrige T05).
2. Ajouter des mots-clés administratifs français : rapport, activité… (corrige A04).
3. Ajouter les préfixes nomic pour la sélection par embeddings.
4. Ajouter aux prompts des agents deux règles : « répondre dans la langue de l'email » et « aucun placeholder ».
5. Relancer l'évaluation des réponses sur les 14 emails avec un juge différent.
