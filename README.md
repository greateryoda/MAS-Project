# Système multi-agent de réponse aux emails (LLM local)

Projet du cours Systèmes Multi-Agents (M2 IA). Un agent de routage reçoit un email,
choisit l'agent spécialisé adapté (étudiant, enseignant, administration, projet de
recherche, générique), et cet agent rédige une proposition de réponse à l'aide d'un LLM
exécuté localement (LM Studio). Un agent critique relit le brouillon, puis l'utilisateur
l'accepte, le modifie, le refuse ou demande une nouvelle version. **Aucun email n'est
envoyé automatiquement** et aucune API distante n'est utilisée.

La documentation technique détaillée est fournie à part (PDF *Technical Documentation –
Multi-Agent Email Response System*).

## Prérequis
- Java 17 ou plus récent (JDK, pour `javac`)
- LM Studio (ou tout serveur compatible avec l'API OpenAI) lancé en local
- Les bibliothèques Jackson sont fournies dans `lib/` (aucune autre dépendance ; l'interface utilise Swing, inclus dans le JDK)

## Configuration du LLM
1. Dans LM Studio, télécharger et charger un modèle (par défaut : `google/gemma-4-e4b`).
2. Démarrer le serveur local (onglet Developer -> Start Server, port 1234).
3. Pour changer de modèle ou d'adresse, modifier `config.json` à la racine du projet
   (`url`, et `model` : l'identifiant affiché dans LM Studio). Pas besoin de recompiler,
   il suffit de relancer l'application.

```json
{
  "url": "http://127.0.0.1:1234/v1/chat/completions",
  "model": "google/gemma-4-e4b",
  "critic": { "enabled": true, "systemPromptFile": "critic/attitude_critic.txt" }
}
```

La page **Local LLM** de l'interface affiche l'adresse, le modèle configuré, l'état de la
connexion et la liste des modèles disponibles sur le serveur, et permet d'envoyer un
prompt de test.

## Compilation et exécution (depuis la racine du projet)
Avec make :

    make            # compile puis lance l'interface graphique
    make gui        # idem
    make console    # ancienne version terminal (Main.java)
    make test       # scénarios de test automatiques (LLM simulé, sans LM Studio)
    make build      # compile seulement
    make clean      # supprime bin/, bin-test/ et build/

Sans make (macOS / Linux) :

    javac --release 17 -cp "lib/*" -d bin src/*.java
    java -cp "bin:lib/*" App          # interface graphique
    java -cp "bin:lib/*" Main         # version terminal

Sous Windows, remplacer `:` par `;` dans le classpath : `java -cp "bin;lib/*" App`.

L'application doit être lancée **depuis la racine du projet** (les chemins de
`config.json` et de `agents/` sont relatifs).

## Interface graphique
| Page | Contenu |
|------|---------|
| Dashboard | agents chargés, état du LLM, statistiques de la session, activité récente, stratégie de routage |
| Process email | saisie (ou chargement d'un email de démonstration), aperçu, traitement, visualisation du workflow en 10 étapes (Pending / Processing / Completed / Failed / Skipped), décision de routage (agent, confiance, signaux, agents impliqués, graphe des interactions), proposition de réponse et validation |
| Agents | liste des agents ; modification de la description, des mots-clés, de `maxRetries`, du system prompt et du contexte ; import d'un fichier texte comme contexte ; consultation de la mémoire ; création d'un nouvel agent |
| Local LLM | serveur, modèle, connexion, prompt de test, `config.json`, activation et prompt du CriticAgent |

Les modifications faites dans la page Agents sont écrites dans les fichiers de l'agent
et appliquées immédiatement (l'agent relit sa configuration sans perdre sa mémoire).

## Emails de démonstration
`demo/emails.json` contient 6 emails, chargeables depuis la page *Process email* :
1. étudiant, question sur l'examen → `StudentAgent`
2. enseignante, proposition de réunion → `TeacherAgent`
3. scolarité, documents et échéance → `AdministrationAgent`
4. coordinatrice du projet SuperMASProject → `ResearchProjectAgent`
5. email ambigu (inscription à un workshop du projet, expéditeur inconnu) → conflit de signaux, arbitrage par le LLM
6. relance de l'email 1 dans le même fil → reconnu grâce à la mémoire du fil (à traiter après avoir accepté l'email 1)

## Routage
Le `RoutingAgent` ne s'arrête pas au premier indice : plusieurs signaux votent
chacun pour un agent, avec une confiance entre 0 et 1.

| Signal        | Classe        | Idée                                          |
|---------------|---------------|-----------------------------------------------|
| fil           | `ThreadStep`  | un agent a déjà traité ce fil de discussion   |
| contact       | `ContactStep` | l'expéditeur est dans `agents/contacts.json`  |
| mots-clés     | `KeywordStep` | mots-clés de l'agent dans le sujet/corps      |
| domaine       | `DomainStep`  | domaine de l'adresse (ou boîte administrative : `scolarite@`, `rh@`, ...), si le contact est inconnu |

Les votes sont additionnés par agent. Si le gagnant a au moins 75 % du total
(signaux concordants) et un score d'au moins 0,7, il est choisi directement. Sinon
(signaux faibles ou contradictoires), le LLM local est consulté comme arbitre
(`LlmClassifierStep`) : en cas de conflit il tranche entre les agents concernés,
sinon il choisit parmi tous les agents, et son choix l'emporte s'il est assez
sûr (≥ 0,6). Sinon, l'email va au `GenericAgent`.

## Agents
| Agent | Rôle | Contexte |
|-------|------|----------|
| `StudentAgent` | étudiants : cours, TP, rendus, examens | `agents/student/` |
| `TeacherAgent` | collègues : enseignement, réunions, jurys | `agents/teacher/` |
| `AdministrationAgent` | administration : inscriptions, documents, signatures, rendez-vous (ton formel) | `agents/administration/` |
| `ResearchProjectAgent` | projet SuperMASProject : membres, livrables, deadlines, réunions (2 fichiers de contexte) | `agents/research/` |
| `GenericAgent` | tout le reste, agent par défaut | `agents/generic/` |
| `CriticAgent` | agent de support (non routable) : note chaque brouillon et propose des remarques | `critic/` |

## Validation par l'utilisateur
Chaque brouillon est montré à l'utilisateur (interface graphique ou terminal), qui peut :
- **accepter** : la réponse est retenue telle quelle ;
- **modifier** : l'utilisateur corrige le texte puis valide la version modifiée ;
- **demander un nouveau brouillon** avec des remarques (dans le terminal : refuser `r`) ;
- **refuser** définitivement (`d` dans le terminal) : rien n'est mémorisé.

Côté BDI, l'agent adopte une **intention** (`Intention`) : répondre à l'email.
Son **désir** est d'obtenir une réponse validée, en au plus `maxRetries` nouveaux
essais après le premier brouillon (`desires.maxRetries` dans `agent.json`). Le
plan suivi est : rédiger → (relecture par le CriticAgent) → soumettre à l'utilisateur →
mémoriser si accepté, sinon rédiger à nouveau. Au-delà de `maxRetries` refus, l'intention est
abandonnée : l'utilisateur peut alors écrire la réponse lui-même (elle est
mémorisée comme une réponse modifiée) ou ne rien envoyer.

La mémoire du fil n'est mise à jour qu'après validation : l'email reçu et la
réponse réellement retenue (éventuellement modifiée) y sont ajoutés. Les
brouillons refusés ne sont jamais mémorisés.

## Organisation
    src/
      App.java                      point d'entrée de l'interface graphique
      Main.java                     point d'entrée terminal (version d'origine)
      EmailAssistant.java           façade : construit le système (registre, routage, critique)
      RoutingAgent.java             combine les signaux, choisit l'agent, gère la boucle de validation, publie les étapes
      RoutingStep.java, ThreadStep.java, ContactStep.java, KeywordStep.java, DomainStep.java
      Arbiter.java, LlmClassifierStep.java  arbitre (LLM) en cas de doute
      RoutingDecision.java          agent choisi + confiance + raison
      WorkflowListener.java, WorkflowStage.java, StageStatus.java  observation des étapes (interface)
      Agent.java, BDIAgent.java     agents spécialisés
      AgentFactory.java             crée un BDIAgent à partir d'un dossier de agents/
      AgentRegistry.java            agents chargés + édition de leurs fichiers + création d'agents
      AgentConfig.java              lecture de agent.json
      CriticAgent.java, Critique.java  relecture des brouillons
      Belief.java, BeliefBase.java  croyances : contexte de base + mémoire des fils
      Intention.java, Proposal.java, Feedback.java
      Validator.java, ConsoleValidator.java, SwingValidator.java
      LLM.java, LmStudioClient.java, LlmException.java
      Email.java, TemporaryContactBook.java, DemoEmail.java
      MainWindow.java, Page.java, DashboardPage.java, ProcessPage.java, AgentsPage.java, LlmPage.java
      ComposePanel.java, EmailPreviewDialog.java, WorkflowPanel.java, RoutingPanel.java,
      AgentNetworkView.java, ProposalPanel.java, NewAgentDialog.java
      ActivityLog.java, ConnectionStatus.java
      Theme.java, Card.java, PillButton.java, Badge.java, Avatar.java, NavIcon.java,
      RoundBorder.java, ThinScrollBarUI.java, ScrollablePanel.java   composants visuels
    test/ScenarioTests.java         scénarios automatiques (make test)
    config.json                     adresse du serveur LLM, modèle, CriticAgent
    critic/attitude_critic.txt      system prompt du CriticAgent
    demo/emails.json                emails de démonstration
    agents/
      <agent>/agent.json            configuration (nom, rôle, description, mots-clés, fichiers, maxRetries)
      <agent>/attitude_*.txt        system prompt
      <agent>/*.txt                 contexte (format : date | contenu)
      <agent>/learnt_beliefs_*.txt  mémoire des fils (générée après validation, non versionnée)
      contacts.json                 adresse, nom et rôle des contacts connus

## Ajouter un agent
Depuis l'interface : page **Agents** → **+ New agent** (nom, rôle, description,
mots-clés, system prompt, contexte). L'agent est créé dans `agents/<role>/` et
devient routable immédiatement.

À la main : créer un dossier `agents/<nom>/` contenant un `agent.json` (voir les agents
existants), un fichier de system prompt et un fichier de contexte. Dans
`agent.json`, renseigner `description` (utilisée par l'arbitre LLM) et
`keywords` (utilisés par `KeywordStep`). L'agent est chargé automatiquement au
démarrage, sans modifier le code. Pour lui associer des expéditeurs connus,
ajouter leurs adresses avec le même `role` dans `agents/contacts.json`.

## Tests
`make test` exécute `test/ScenarioTests.java` dans une copie de travail
(`build/test-workspace`) avec un LLM simulé et un validateur scripté : chargement des
agents, routage des 5 emails de démonstration, arbitrage, fallback générique,
acceptation / modification / nouveau brouillon / refus / abandon, mémoire du fil,
CriticAgent, ordre des étapes du workflow, panne du LLM, création et modification
d'agents. Ces tests ne remplacent pas un essai avec un vrai modèle dans LM Studio.
