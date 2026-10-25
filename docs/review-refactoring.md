# Revue de code : refactorisation, `@Transactional`, DSL, coroutines

- Date : 2026-10-20.
- Point de départ : `master` (`f859e42`, après ARC-308).
- Périmètre : tout le code de production (~2 100 lignes) et un survol des tests (~4 400 lignes).
- Rédaction : avec Claude (Anthropic), via Claude Code ; relue par le mainteneur.
- Aucune modification n'a été faite.

## Suivi

Toutes les recommandations ont été appliquées :

| Points | Ticket | PR |
|---|---|---|
| R2, R3 | ARC-309 | #26 |
| R1 | ARC-310 | #27 |
| R4, R5 | ARC-311 | #28 |
| R6 | ARC-312 | #29 |
| R7 | ARC-313 | #30 |
| R8, R9, R10 | ARC-314 | #31 |

Écarts par rapport au texte ci-dessous :

- **R1** : `create` repose finalement sur `Persistable` (l'entité indique à Spring Data qu'elle est nouvelle), et non sur un fragment qui appelle `EntityManager.persist`. Le seul `@Transactional` restant est sur la requête `updateStatus`.
- **R1** : la transition conditionnelle ne rend pas la garantie « jamais AVAILABLE sans contenu » totalement indépendante du réglage. La réconciliation supprime le contenu avant d'enregistrer FAILED, et il reste une fenêtre de quelques millisecondes. La vérification au démarrage « délai de grâce > `api-call-timeout` » est donc conservée.

## 0. Synthèse

Le code est sain. L'architecture hexagonale est respectée, le domaine ne dépend d'aucun framework, les invariants sont portés par le modèle et les erreurs sont typées. Les points ci-dessous visent un niveau « senior », pas des corrections de bugs, sauf **R1**, qui touche les performances et la concurrence.

| # | Sujet | Gain | Effort | Priorité |
|---|---|---|---|---|
| R1 | `save` fait un `SELECT` avant chaque écriture ; les transitions ne sont pas atomiques | Perf. + concurrence | M | **Haute** |
| R2 | `DocumentController.search` : 8 paramètres | Lisibilité, API | S | **Haute** |
| R3 | Résolution du `AuthenticatedCaller` répétée dans chaque endpoint | DRY, sécurité | S | Moyenne |
| R4 | Validation et algorithme dupliqués entre `ResumeDeletionsService` et `AbandonStalledArchivingsService` | DRY | S | Moyenne |
| R5 | `ArchivedDocument` : 8 paramètres, cycle de vie dispersé | Modèle | M | Moyenne |
| R6 | `DocumentApi` : 26 réponses d'erreur recopiées (249 lignes) | Bruit | S | Moyenne |
| R7 | Taille vérifiée deux fois ; `mediaType` exposé par la policy | Cohésion | S | Basse |
| R8 | Noms d'attributs JPA en chaînes dans la `Specification` | Sûreté | XS | Basse |
| R9 | Constructeurs de documents de test recopiés dans 7 fichiers | Tests | S | Basse |
| R10 | Petits écarts (chemin d'API dupliqué, `ProblemDetail` bâti à la main, `DocumentId.parse` inutilisé en prod) | Cohérence | XS | Basse |

Sur les trois questions de fond :

- **`@Transactional`** : ne **pas** en ajouter sur les services. L'absence actuelle est un choix correct.
- **DSL** : déjà utilisés là où ils apportent quelque chose (Spring Security, MockMvc). Ne pas en ajouter ailleurs.
- **Coroutines** : **non pertinentes** sur cette pile. Si un besoin de montée en charge apparaît, activer plutôt les threads virtuels.

---

## 1. Méthodes et constructeurs de plus de 7 paramètres

| Élément | Paramètres | Verdict |
|---|---|---|
| `DocumentController.search` | 8 (7 `@RequestParam` + `jwt`) | **À refactoriser** (R2) |
| `ArchivedDocument(...)` | 8 | **À refactoriser** (R5) |
| `ArchivedDocumentEntity(...)` | 15 | Acceptable : c'est le reflet d'une table, toujours appelé avec des arguments nommés depuis un seul endroit (`toEntity`). Le découper en `@Embeddable` alourdirait le mapping sans gain métier |
| `ArchivedDocumentResponse(...)` | 11 | Acceptable : DTO plat de sérialisation, construit par la seule fonction `from` |

Juste en dessous du seuil, mais à surveiller : `ArchivedDocument.create` (6), `ResumeDeletionsService` et `AbandonStalledArchivingsService` (5 chacun, dont 2 dupliqués, voir R4).

---

## 2. Refactorisations

### R1 : `save` coûte deux requêtes et écrase l'état sans contrôle (**haute**)

**Constat.** `DocumentRepositoryAdapter.save` reconstruit une entité neuve, avec un identifiant renseigné et sans `@Version`. Pour Spring Data, une telle entité n'est jamais « nouvelle » : `SimpleJpaRepository.save` passe par `em.merge`, qui fait **un `SELECT` puis un `INSERT` ou un `UPDATE`**. Un archivage coûte donc 4 requêtes au lieu de 2, et une suppression 4 au lieu de 2.

De plus, chaque `save` remplace l'état complet, sans condition. Le scénario noté en ARC-308 en découle : un `AVAILABLE` écrit tardivement pourrait écraser un `FAILED`. Il n'est aujourd'hui évité que par la règle `grace-period > api-call-timeout`.

**Proposition.** Distinguer la création de la transition dans le port :

```kotlin
interface DocumentRepository {
    fun create(document: ArchivedDocument)                            // INSERT seul (persist)
    fun transition(document: ArchivedDocument, from: DocumentStatus): Boolean
    // UPDATE ... SET status, updated_at, deleted_at WHERE id = ? AND status = :from
}
```

- `create` : `ArchivedDocumentEntity` implémente `Persistable<UUID>` (`isNew = true` à la création), ou bien on appelle `entityManager.persist`.
- `transition` : une requête `@Modifying` conditionnelle. Le statut agit comme un *compare-and-set* : c'est un verrouillage optimiste **sans colonne de version**, et le domaine n'a pas à transporter de version.
- Quand `transition` renvoie `false`, le document a changé entre-temps. Le service décide alors : ignorer (réconciliation concurrente) ou lever une erreur.

**Gains** : une requête par écriture au lieu de deux ; la garantie « jamais AVAILABLE sans contenu » ne repose plus que sur une fenêtre de quelques millisecondes (voir le suivi) ; les doubles réconciliations deviennent observables, sans écriture inutile.

**Coût** : la signature du port change, ce qui touche 4 services et leurs tests unitaires. Il faut un `@Transactional` sur la requête `@Modifying` : c'est le seul `@Transactional` justifié (voir §3).

### R2 : `search` à 8 paramètres (**haute**)

Lier les paramètres de requête à un objet :

```kotlin
data class SearchDocumentsRequest(
    val documentType: String? = null, val source: String? = null, val businessReference: String? = null,
    val createdFrom: Instant? = null, val createdTo: Instant? = null,
    val page: Int = PageRequest.FIRST_PAGE, val size: Int = PageRequest.DEFAULT_SIZE,
) {
    fun toCriteria() = DocumentSearchCriteria(documentType?.let(::DocumentType), source, businessReference, createdFrom, createdTo)
    fun toPage() = PageRequest(page, size)
}

override fun search(@ParameterObject request: SearchDocumentsRequest, caller: AuthenticatedCaller): DocumentPageResponse
```

- Spring MVC lie une data class Kotlin par son constructeur, sans annotation sur les paramètres.
- `@ParameterObject` (springdoc) garde chaque filtre documenté comme paramètre de requête.
- Les valeurs par défaut de page et de taille quittent le contrôleur, ce qui supprime les `?:`.
- Point à vérifier : un `createdFrom` mal formé doit toujours répondre 400 avec le même `code`. Une erreur de liaison donne une `MethodArgumentNotValidException`, ou une `BindException`, au lieu d'une `MethodArgumentTypeMismatchException`. Il faut un test.

### R3 : `callerResolver.resolve(jwt)` dans chaque endpoint (**moyenne**)

Les 5 endpoints reçoivent `@AuthenticationPrincipal jwt: Jwt` pour le convertir aussitôt. Un `HandlerMethodArgumentResolver` qui fournit directement `AuthenticatedCaller` :

- retire `Jwt` et `JwtCallerResolver` du contrôleur, ce qui lui enlève une dépendance ;
- garantit que la résolution du tenant ne peut pas être oubliée sur un futur endpoint ;
- conserve le 403 `caller-not-identified`.

À écarter : un `JwtAuthenticationConverter` personnalisé. Une revendication manquante y deviendrait une erreur d'authentification, donc un **401** au lieu du 403, ce qui change le contrat.

### R4 : deux services de réconciliation jumeaux (**moyenne**)

`ResumeDeletionsService` et `AbandonStalledArchivingsService` sont identiques, à deux choses près : le statut cherché et l'étape appliquée. Les deux `require` sont recopiés à l'identique. Je propose un objet valeur du domaine :

```kotlin
class StalledDocumentsPolicy(val gracePeriod: Duration, val batchSize: Int) {
    init { require(!gracePeriod.isNegative) { ... }; require(batchSize > 0) { ... } }
    fun stalledBefore(now: Instant): Instant = now.minus(gracePeriod)
}
```

- Chaque service tient alors en 3 lignes, et `ArchivingConfiguration` construit la politique une seule fois depuis `ReconciliationProperties`.
- Je déconseille d'aller plus loin, avec un service générique paramétré par statut et lambda : ce serait l'abstraction prématurée que CLAUDE.md exclut, pour deux usages seulement.
- Même raisonnement pour `DocumentDeletion` et `ArchivingFailure` : ils se ressemblent mais portent deux transitions métier distinctes. **Les garder séparés.**

### R5 : `ArchivedDocument` à 8 paramètres (**moyenne**)

Les quatre derniers paramètres (`status`, `createdAt`, `updatedAt`, `deletedAt`) forment un tout. Les invariants (`updatedAt >= createdAt`, `deletedAt` présent si et seulement si DELETED) et les transitions s'appliquent tous à ce groupe. Je propose de l'extraire :

```kotlin
data class DocumentLifecycle(val status: DocumentStatus, val createdAt: Instant, val updatedAt: Instant, val deletedAt: Instant?) {
    init { /* les deux invariants actuels */ }
    fun moveTo(target: DocumentStatus, at: Instant): DocumentLifecycle
    companion object { fun start(at: Instant) = DocumentLifecycle(DocumentStatus.INITIAL, at, at, null) }
}
class ArchivedDocument(val id: DocumentId, val owner: DocumentOwner, val metadata: DocumentMetadata, val file: StoredFile, val lifecycle: DocumentLifecycle)
```

- On passe de 8 à 5 paramètres, et chaque composant devient un objet valeur.
- L'agrégat ne garde que l'identité et la délégation : `markAvailable(at) = copy(lifecycle = lifecycle.moveTo(AVAILABLE, at))`.
- Coût : les appels `document.status` deviennent `document.lifecycle.status` (ou une propriété déléguée `val status get() = lifecycle.status`, pour limiter l'impact). Les mappers d'entité et de réponse sont à adapter.
- À faire **après** R1, qui touche les mêmes fichiers.

### R6 : `DocumentApi` recopie 26 fois la réponse Problem Details (**moyenne**)

Le bloc `content = [Content(mediaType = PROBLEM, schema = Schema(implementation = ProblemDetail::class))]` apparaît 26 fois. Les réponses 401, 403, 500 et 503 sont les mêmes sur toutes les opérations.

Un `OpenApiCustomizer`, ou un `OperationCustomizer`, ajouté dans `OpenApiConfiguration` :

- ajoute ces réponses communes à chaque opération ;
- associe le schéma `ProblemDetail` à toute réponse 4xx ou 5xx.

L'interface ne garde ainsi que les réponses propres à chaque opération (201, 404, 413, 415…), soit environ 249 → 120 lignes. Un test sur `/v3/api-docs` existe déjà ou est simple à écrire : il verrouillera le résultat.

### R7 : cohésion de l'archivage (**basse**)

- La taille est vérifiée deux fois : `sizePolicy.check` dans `ArchiveDocumentService.archive`, puis dans `ArchivedDocument.create`.
  - Le premier appel sert l'ordre des erreurs : 413 avant 415.
  - Le second protège l'invariant de l'agrégat.
  - Garder celui de `create` et retirer `sizePolicy` du service, à condition que l'ordre 413 puis 415 ne soit pas un contrat. Sinon, retirer le paramètre de `create`, en considérant qu'un document est valide par construction de `StoredFile`.
- `formatPolicy.mediaType(...)` est un utilitaire de normalisation exposé, que le service appelle après `check`. `check` pourrait plutôt renvoyer un `AcceptedFormat(format, mediaType)`, et le service n'aurait plus à connaître la normalisation.
- `archive()` (~30 lignes) enchaîne validation, création, écriture, envoi et finalisation. Extraire `validate(command): ArchivedDocument` (le `pending`) et `upload(pending, stream)` rendrait le flux lisible d'un coup d'œil.

### R8 : `Specification` avec des noms en chaînes (**basse**)

`root.get<String>("tenantId")` : une faute de frappe ou un renommage ne se voit qu'à l'exécution. Le correctif ne demande aucune dépendance : `root.get<String>(ArchivedDocumentEntity::tenantId.name)`. Voir §4 pour les alternatives fondées sur un DSL.

### R9 : constructeurs de documents de test recopiés (**basse**)

Chaque test de service a sa propre fonction `pending()`, `deleting()` ou `document()` (7 fichiers). Je propose une seule fonction `aDocument(status = ..., tenant = ..., updatedAt = ...)` dans `domain/src/test/.../fixtures`, partagée avec `infrastructure` via un `test-jar` Maven (`maven-jar-plugin`, objectif `test-jar`, dépendance `<type>test-jar</type>`, portée `test`).

### R10 : cohérence (**basse**)

- `"/api/v1/documents"` est écrit dans `DocumentController` et dans `SecurityConfiguration`. Une constante partagée, par exemple `DocumentApi.PATH`, éviterait une divergence silencieuse.
- `ProblemDetailsSecurityErrorHandler` construit un `linkedMapOf` à la main, alors que `ApiExceptionHandler` utilise `ProblemDetail`. Utiliser `ProblemDetail.forStatusAndDetail(...)` et `setProperty("code", …)` garantirait le même format partout.
- `DocumentId.parse` n'est utilisé que dans les tests : le supprimer, ou le justifier.
- `AuthenticatedCaller` et `DocumentOwner` ont la même forme `(tenantId, source)`. Les garder distincts reste défendable : l'un est l'identité de l'appelant, l'autre la propriété du document. À laisser en l'état.

---

## 3. `@Transactional` : nécessité et utilité

**État actuel.** Il n'y a aucun `@Transactional` dans le code de production. Chaque appel au dépôt s'exécute dans la transaction que `SimpleJpaRepository` ouvre lui-même ; `findAll(spec, pageable)` fait le `SELECT` et le `COUNT` dans une même transaction en lecture seule. `open-in-view` est désactivé, et la conversion entité → domaine se fait dans l'adaptateur, sans chargement paresseux (les `tags` sont une colonne JSON).

**Faut-il une transaction autour des services ?** **Non**, et ce serait même une régression :

1. **Les écritures intermédiaires doivent être validées.** La réconciliation repose sur une ligne PENDING ou DELETING **validée avant** l'appel au stockage. Dans une seule transaction, un arrêt de l'instance annulerait la ligne alors que l'objet peut déjà être dans le bucket : on retrouverait exactement le contenu orphelin et invisible corrigé en ARC-308.
2. **Une connexion serait monopolisée pendant l'envoi.** Un envoi de 50 Mo peut durer jusqu'à 2 min. Avec le pool Hikari par défaut (10 connexions), 10 envois simultanés bloqueraient toute l'API, lectures comprises.
3. **Il n'y a pas d'atomicité à obtenir.** Base et stockage ne partagent aucune transaction. La cohérence vient de l'ordre des écritures et de la réconciliation (un *saga* local), pas d'un `COMMIT`.
4. **Le domaine doit rester indépendant de Spring.** Un `@Transactional` sur les services du domaine est exclu. Il faudrait un décorateur dans l'infrastructure, ce qui n'a de sens que si un vrai besoin existe.

**Où une transaction serait justifiée :**

- sur la requête conditionnelle `@Modifying` de R1, où elle est obligatoire (les méthodes de requête héritent du `readOnly = true` de `SimpleJpaRepository`) ;
- un jour, si un cas d'usage écrit **plusieurs lignes en base sans appel externe entre elles**, par exemple un journal d'audit écrit avec la transition. Ce serait alors un `TransactionTemplate` placé dans un adaptateur.

`@Transactional(readOnly = true)` sur les lectures de l'adaptateur n'apporterait presque rien : une seule requête, déjà en lecture seule côté Spring Data.

**Recommandation** : garder l'état actuel et le documenter dans la KDoc de l'adaptateur, ce qui est déjà fait (« Each call runs in its own transaction »). N'ajouter `@Transactional` qu'avec R1.

---

## 4. DSL : utilité et pertinence

| DSL | État | Verdict |
|---|---|---|
| Spring Security Kotlin (`http { }`) | Utilisé | Garder : plus lisible que la chaîne de *builders* |
| MockMvc Kotlin (`mockMvc.get { }`) | Utilisé dans les tests | Garder |
| Requêtes typées : Kotlin JDSL, jOOQ, Exposed | Absent | **Non** pour l'instant. Une seule requête dynamique de 7 prédicats : la `Specification` suffit, et R8 apporte la sûreté de nommage sans dépendance. À reconsidérer si les recherches se multiplient (tri dynamique, jointures, plein texte). Kotlin JDSL serait alors le choix le moins intrusif avec Spring Data JPA ; jOOQ seulement en abandonnant JPA |
| Enregistrement des beans (`BeanRegistrarDsl`, Spring Framework 7) | Absent | **Non.** `ArchivingConfiguration` (86 lignes de `@Bean`) ne serait pas plus court. On perdrait la convention attendue par tout développeur Spring Boot et la lisibilité des conditions (`@ConditionalOn…`), sans autre gain que le style |
| Routes fonctionnelles (`router { }`) | Absent | **Non.** springdoc documente mal les routes fonctionnelles, et le contrat OpenAPI est un livrable de ce service |
| DSL métier interne (par exemple pour construire un `ArchivedDocument`) | Absent | **Non** en production. En test, la fonction de R9 avec des paramètres nommés par défaut remplit ce rôle sans *builder* |

---

## 5. Coroutines : utilité et pertinence

**Verdict : non pertinentes ici.**

- **Toute la pile est bloquante.** Spring MVC sur servlet, JPA et JDBC, et un S3 `S3Client` synchrone (le client Netty est même exclu du `pom`). Une coroutine qui appelle du code bloquant n'y gagne rien : il faudrait `Dispatchers.IO` partout, avec un coût en complexité et rien en débit.
- **JPA ne s'y prête pas.** `EntityManager` et les transactions sont liés au thread, et un changement de thread à une suspension casse ce modèle. Les coroutines n'ont de sens qu'avec une pile réactive complète : WebFlux, R2DBC (sans JPA, donc avec un autre mapping et un autre Liquibase), et `S3AsyncClient`. Ce serait une réécriture de l'infrastructure.
- **Le domaine en souffrirait.** Des ports `suspend` imposent `kotlinx-coroutines` à tout le domaine et à tous ses tests, sans besoin métier.
- **Le parallélisme n'est pas un besoin.** La réconciliation traite au plus 100 documents toutes les 5 min, en séquence. C'est volontaire : on s'arrête à la première panne de stockage.

**Si la montée en charge devient un sujet** (beaucoup d'envois longs simultanés) : `spring.threads.virtual.enabled=true`, sur JDK 21. On garde le code bloquant, et chaque requête ou appel S3 occupe un thread virtuel au lieu d'un thread Tomcat.

- Points d'attention sous JDK 21 : le *pinning* sur les blocs `synchronized` (corrigé en JDK 24, JEP 491).
- Le vrai goulot reste le pool de connexions JDBC. Il faut mesurer avant d'activer, avec un test de charge sur l'envoi.

---

## 6. Ordre proposé

1. **R2, R3** (contrôleur) : un ticket court, sans effet sur le domaine.
2. **R1** (dépôt : `create` + `transition`), qui apporte le seul `@Transactional` utile.
3. **R4, R5** (domaine) : à enchaîner après R1, qui touche les mêmes services.
4. **R6, R7, R8, R9, R10** : nettoyage, au fil de l'eau.

Chaque étape est indépendante, testable avec la suite actuelle et livrable en petits commits.
