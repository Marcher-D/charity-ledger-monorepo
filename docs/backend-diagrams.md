# PlantUML diagrams for the backend presentation

Render at https://www.plantuml.com/plantuml or with the VS Code PlantUML extension.
Dark-styled to blend into a dark slide deck. Companion to BACKEND_PRESENTATION.md.

## 1. Anchor architecture (show after "Backend Jobs", revisit every section)

```plantuml
@startuml
skinparam backgroundColor #16181d
skinparam defaultFontColor #e8e6e3
skinparam defaultFontSize 15
skinparam ArrowColor #4ade80
skinparam ArrowFontColor #9ca3af
skinparam rectangle {
  BackgroundColor #1f2937
  BorderColor #4ade80
  FontColor #e8e6e3
}
skinparam database {
  BackgroundColor #1f2937
  BorderColor #38bdf8
  FontColor #e8e6e3
}
skinparam queue {
  BackgroundColor #1f2937
  BorderColor #f59e0b
  FontColor #e8e6e3
}

rectangle "Frontend\n(React + nginx)" as FE
rectangle "**BACKEND**\nSpring Boot" as BE #234034
database "PostgreSQL\n(web truth)" as DB
rectangle "Orchestrator" as ORCH
queue "mempool queue" as MQ1
queue "event bus queue" as MQ2
rectangle "Mempool service" as MEM
collections "node-0 … node-N\n(one per user/campaign)\n(chain truth)" as NODES

FE -right-> BE : REST (JWT)
BE -right-> DB : balances, campaigns,\ntransaction records
BE -down-> ORCH : "spawn a node for\nthis new user"
ORCH -right-> NODES : docker run
BE -down-> MQ1 : signed transaction JSON
MQ1 -right-> MEM
MEM -up-> NODES : nodes pull batches
NODES .up.> BE : sign requests answered\n(user keys stay in nodes)
NODES -left-> MQ2 : VALIDATED / REJECTED /\nBLOCK_FINALIZED (reward)
MQ2 -up-> BE : settle outcomes
@enduml
```

## 2. Escrow state machine (one lifecycle for every money action)

```plantuml
@startuml
skinparam backgroundColor #16181d
skinparam defaultFontColor #e8e6e3
skinparam ArrowColor #4ade80
skinparam state {
  BackgroundColor #1f2937
  BorderColor #4ade80
  FontColor #e8e6e3
}

[*] --> PENDING : user acts (donate 100)\n**available −100 → pending +100**\nfunds are now LOCKED
PENDING --> SUCCESS : chain says TXN_VALIDATED\npending −100, effect applied\n(campaign +100)
PENDING --> FAILED : chain says TXN_REJECTED\n**pending −100 → available +100**\n(automatic refund)
PENDING --> FAILED : still pending after 15 min\n(message lost in a shutdown)\n→ expiry job refunds
SUCCESS --> [*]
FAILED --> [*]

note right of PENDING
  While consensus decides,
  the money exists in exactly
  one place — no double spend,
  no stuck funds.
end note
@enduml
```

## 3. Life of a donation (closing walkthrough / demo narration)

```plantuml
@startuml
skinparam backgroundColor #16181d
skinparam defaultFontColor #e8e6e3
skinparam ArrowColor #4ade80
skinparam sequence {
  ParticipantBackgroundColor #1f2937
  ParticipantBorderColor #4ade80
  ParticipantFontColor #e8e6e3
  LifeLineBorderColor #4b5563
  ActorBorderColor #4ade80
}

actor Donor
participant "Backend" as BE
participant "Donor's own node\n(holds the private key)" as NODE
queue "mempool\nqueue" as Q1
collections "Validators\n(BFT consensus)" as VAL
queue "event bus\nqueue" as Q2

Donor -> BE : Donate 100
BE -> BE : lock funds\navailable→pending\nrecord PENDING
BE -> NODE : "sign this transaction"
NODE --> BE : signature + txId
BE -> Q1 : publish signed tx
Q1 -> VAL : node pulls it into a block
VAL -> VAL : consensus round\n(propose, vote, ≥68% certify)
VAL -> Q2 : TXN_VALIDATED\n+ BLOCK_FINALIZED (2% reward)
Q2 -> BE : (deduplicated — every\nnode announces it)
BE -> BE : settle SUCCESS\ncampaign +100, proposer +2
Donor <- BE : dashboard polls →\nPENDING flips to SUCCESS
note over Donor, Q2 : whole journey ≈ 10–30 seconds
@enduml
```

## 4. Restart resilience (chain ephemeral, database durable)

```plantuml
@startuml
skinparam backgroundColor #16181d
skinparam defaultFontColor #e8e6e3
skinparam ArrowColor #4ade80
skinparam activity {
  BackgroundColor #1f2937
  BorderColor #4ade80
  FontColor #e8e6e3
  DiamondBackgroundColor #1f2937
  DiamondBorderColor #f59e0b
  DiamondFontColor #e8e6e3
}

start
:docker compose up;
:orchestrator wipes chain files,
starts a FRESH chain (round 0),
bumps the **chain epoch**;
:relaunch one node per
known user / campaign;
if (backend: epoch changed?) then (yes)
  :REPLAY every user's balance:
  system deposit per user,
  then re-stake what was staked;
  :mark epoch as reconciled
  (so replay runs exactly once);
else (no)
  :nothing to do;
endif
:expiry job (always running):
any PENDING older than 15 min
→ FAILED + automatic refund;
stop
@enduml
```
