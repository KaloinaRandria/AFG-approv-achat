# Plan de migration : référentiel utilisateurs commun (AFG-approv-achat + AFG-process)

> Statut : décisions validées ; phase 0 commitée, phase 1 prête (non exécutée). Rédigé le 26/11/2026 à partir de l'état des deux dépôts
> (achat : `fb8a0d7`, process : `ffc6c8b`).

## 1. Objectif

Un utilisateur, son poste, sa direction, son supérieur et ses valideurs sont créés **une seule fois**,
dans un référentiel commun, et utilisés par les deux applications. Chaque application garde ses
**propres habilitations** (rôles), qui n'ont pas de sens d'une appli à l'autre.

## 2. Architecture cible

Une seule base PostgreSQL (serveur `10.25.10.151`), trois schémas :

```
Base ACHAT_APPROV_xxx (renommage éventuel plus tard, ex. AFG_xxx)
├── referentiel  utilisateur, poste, service (= direction), pdp,
│                utilisateur_validateur, demande_compte
├── public       tables de l'appli achat (inchangées), dont role et utilisateur_role
└── process      tables de l'appli process, dont role et utilisateur_role
```

| Qui | Schéma `referentiel` | Schéma `public` (achat) | Schéma `process` |
|---|---|---|---|
| Appli achat (login `app_achat`) | lecture + écriture (**propriétaire**) | lecture + écriture | aucun accès |
| Appli process (login `app_process`) | **lecture seule** | aucun accès | lecture + écriture |

- L'appli achat reste **propriétaire** du référentiel : elle a déjà le modèle le plus riche (poste,
  supérieur, valideurs, photo) et le circuit maker / checker de `DemandeCompte`.
- L'appli process lit les utilisateurs et directions, et ne gère plus que **l'attribution de ses
  rôles** (ADMIN, IT, MAKER, CHECKER) à des utilisateurs qui existent déjà dans le référentiel.
- Les clés étrangères restent de vraies FK PostgreSQL (même base), et les `@ManyToOne` vers
  `Utilisateur` continuent de fonctionner sans réécrire le code métier.

### Pourquoi ce choix plutôt que deux datasources

Côté achat, `Utilisateur` est référencé par **23 colonnes dans 11 entités** (inventaire au §4).
Avec une base séparée, chacune deviendrait un simple identifiant, sans FK ni jointure, et toutes les
requêtes JPQL du type `JOIN dm.demandeur u JOIN u.service s` (`DemandeMereRepo`) seraient à réécrire.

## 3. Décisions

Toutes validées le 26/11/2026.

| # | Question | Décision |
|---|---|---|
| D1 | Qui crée / modifie les utilisateurs ? | L'appli achat seule (écran Organisation + `DemandeCompte`). |
| D2 | Les rôles sont-ils communs ou par appli ? | Par appli : `public.utilisateur_role` et `process.utilisateur_role`. |
| D3 | Qui gère les directions ? Aujourd'hui **les deux** (`OrganisationService` côté achat, `DirectionController` côté process). | Achat seule ; l'écran Directions de process passe en lecture. |
| D4 | Les valideurs (`utilisateur_validateur`) sont-ils communs ? | Oui, dans `referentiel` (process ne les utilise pas encore). |
| D5 | Garde-t-on les noms actuels de l'achat (`service`, `id_service`) ou renomme-t-on en `direction` ? | On garde les noms actuels ; renommage éventuel plus tard. |
| D6 | Une base fusionnée, ou on garde `PROCESS_xxx` à part ? | Fusionner : c'est la condition pour avoir de vraies FK. |
| D7 | Nom de la base PROD de process ? | Il n'y a **pas encore de base PROD** pour process sur le serveur (seulement `PROCESS_PREPROD` et `PROCESS_UAT`). En PROD, process sera donc installé directement dans le schéma `process` de `ACHAT_APPROV_PROD`, sans reprise de données ; l'import de la phase 2 ne concerne que la PREPROD (et l'UAT si elle doit être conservée). |

## 4. Inventaire de l'existant

### 4.1 Appli achat (`AFG-approv-achat`, Spring Boot 3.3.5)

**Tables qui partent dans `referentiel`**

| Table | Entité | Remarque |
|---|---|---|
| `utilisateur` | `model/utilisateur/Utilisateur` | nom, prenom, mail, contact, actif, id_superieur, id_pdp, id_poste, id_service |
| `poste` | `model/utilisateur/Poste` | FK `id_service` (champ Java `direction`) |
| `service` | `model/util/Service` | libelle, acronyme : c'est la **direction** |
| `pdp` | `model/utilisateur/Pdp` | photo de profil |
| `utilisateur_validateur` | `@JoinTable` dans `Utilisateur` | |
| `demande_compte` | `model/utilisateur/DemandeCompte` | circuit maker / checker de gestion des comptes |

**Tables qui restent dans `public` (achat)** : `role`, `utilisateur_role` (rôles ADMIN,
MOYENS_GENERAUX, SG, DFC, CONTROLEUR, AUDIT, FINANCE, FINANCE_CONSULT, CHECKER…).

**Entités métier qui référencent `utilisateur`** (aucune modification Java nécessaire, les FK
suivent la table) :

| Entité | Colonnes |
|---|---|
| `BonCommandeMere` | createur, id_soumis_par, id_valide_mg_par, id_valide_sg_par, id_rejete_par, id_cloture_reception_par |
| `BcContact` | utilisateur |
| `BonSortieMere` | id_cree_par, id_annule_par |
| `DemandeMere` | id_demandeur, id_transmis_finance_par |
| `ValidationDemande` | validateur |
| `PaiementDirect` | id_transmis_par, id_paye_par |
| `Inventaire` | id_cree_par, id_soumis_par, id_valide_par |
| `InventaireEvenement` | id_utilisateur |
| `CommentaireFinance` | id_utilisateur |
| `HistoriqueFinanceDemande` | id_utilisateur |
| `DemandeCompte` | id_utilisateur_cible, id_demandeur, id_decideur |

**SQL hors JPA**

- Vue `v_historique_mouvement_stock` (`scriptSQL/PROD/02_inventaire_stock.sql`) : `LEFT JOIN utilisateur`.
  PostgreSQL garde la vue valide après `SET SCHEMA` (référence interne par OID), mais les scripts qui
  la recréent devront écrire `referentiel.utilisateur`.
- `ResetData` (`TRUNCATE … CASCADE`) ne touche pas aux tables utilisateurs : rien à changer, mais
  il ne devra **jamais** inclure une table de `referentiel`.
- Les requêtes natives (`DemandeMereRepo`, `LotStockRepo`, `VEtatStockRepo`) ne joignent pas les
  tables utilisateurs.

**Authentification** : `MyAuthenticationProvider` vérifie le mot de passe dans l'AD puis charge
l'utilisateur par mail (`UtilisateurService.getUtilisateurByMail`) et ses rôles. Inchangé.

### 4.2 Appli process (`AFG-process`, Spring Boot 4.2.0)

| Table | Entité | Devient |
|---|---|---|
| `utilisateur` | `Utilisateur` (nom, prenom, mail unique, id_direction, actif) | supprimée, remplacée par `referentiel.utilisateur` |
| `direction` | `Direction` (code, libelle, actif) | supprimée, remplacée par `referentiel.service` |
| `role`, `utilisateur_role` | `Role`, `RoleApplication` (ADMIN, IT, MAKER, CHECKER) | restent dans `process` |
| `demande_utilisateur`, `demande_utilisateur_role` | `DemandeUtilisateur` (maker / checker) | restent, mais ne servent plus qu'aux **demandes de rôles** |

FK à réécrire vers les nouveaux identifiants : `evaluation.id_utilisateur`,
`procedure.id_direction`, `demande_utilisateur.id_utilisateur / id_direction / id_maker / id_checker`,
`utilisateur_role.id_utilisateur`.

### 4.3 Écarts de modèle à réconcilier

| achat (`referentiel`) | process | Règle |
|---|---|---|
| `utilisateur.mail` | `utilisateur.mail` (unique) | **clé de rapprochement**, comparée en `lower(trim(mail))` |
| `service.acronyme`, `service.libelle` | `direction.code`, `direction.libelle` | rapprochement par `acronyme = code`, sinon par libellé ; table de correspondance validée à la main |
| — | `direction.actif` | ajouter `actif boolean not null default true` à `referentiel.service` |
| `utilisateur.id_service` | `utilisateur.id_direction` | process mappe `@JoinColumn(name = "id_service")` |

## 5. Plan d'exécution

### Phase 0 — Prérequis (aucun changement fonctionnel)

1. Valider les décisions D1 à D7.
2. **`spring.jpa.hibernate.ddl-auto` passe de `update` à `${DDL_AUTO:validate}` dans les deux applis**
   (fait le 26/11/2026). Avec `update`, Hibernate pourrait recréer une table `utilisateur` vide
   dans `public` ou `process` après le déplacement, ou tenter de modifier les tables du référentiel.
   Conséquence : toute nouvelle entité ou colonne a désormais son script SQL, exécuté avant le
   déploiement. Vérification sans démarrer l'appli : test `SchemaValidationTest` (voir plus bas).
3. **Logins PostgreSQL dédiés** : script `scriptSQL/PROD/05_login_applicatif.sql` (copie dans
   `AFG-process/scriptSQL/login_applicatif.sql`), à exécuter par `postgres` sur chaque base. Le login
   est nommé d'après la base (`achat_approv_preprod_app`, `achat_approv_prod_app`,
   `process_preprod_app`…) car **PREPROD et PROD sont sur le même serveur** et toutes les applis du
   serveur (achat, process, RH, Intranet…) utilisent aujourd'hui le seul compte `postgres`,
   superutilisateur. Ensuite :
   1. `ALTER ROLE <login> PASSWORD '…';` (le mot de passe n'est pas dans Git) ;
   2. dans `application-secret.properties` : `spring.datasource.username=<login>` et le mot de passe ;
   3. démarrer l'appli et faire un tour des écrans principaux.

   Ces logins seront ceux de la phase 1 (`app_achat` / `app_process` dans la suite de ce document
   désignent le login de l'environnement concerné).
4. **Base de répétition** : dans pgAdmin, connecté à la base `postgres`, et sans session ouverte sur
   la base source (applis arrêtées, onglets pgAdmin fermés) :

   ```sql
   CREATE DATABASE "ACHAT_APPROV_MIGR" TEMPLATE "ACHAT_APPROV_PREPROD";
   CREATE DATABASE "PROCESS_MIGR"      TEMPLATE "PROCESS_PREPROD";
   ```

   Pour lancer une appli dessus sans modifier les fichiers :
   `$env:SPRING_DATASOURCE_URL = 'jdbc:postgresql://10.25.10.151:5432/ACHAT_APPROV_MIGR'`.
   Les scripts des phases 1 et 2 sont répétés sur ces bases jusqu'à ce qu'ils passent sans erreur ;
   pour recommencer, `DROP DATABASE` puis recréer depuis le modèle.

**Vérifier que le schéma correspond aux entités** (lecture seule, sans démarrer l'appli ni ses
initialisations) :

```powershell
.\mvnw test "-Dtest=SchemaValidationTest" "-Dschema.validation=true"
```

Le test ne démarre que la couche JPA, en connexion lecture seule, avec `ddl-auto=validate`. Il est
ignoré lors d'un `mvnw test` normal. Variable `SPRING_DATASOURCE_URL` pour viser une autre base.

### Phase 1 — Référentiel dans la base achat (SQL + petit changement Java achat)

**Statut (26/11/2026)** : script et code prêts, **rien n'est exécuté**.

Script [`scriptSQL/PROD/06_schema_referentiel.sql`](../scriptSQL/PROD/06_schema_referentiel.sql), par `postgres`, application arrêtée :

- crée le schéma `referentiel` et y déplace `service`, `poste`, `pdp`, `utilisateur`,
  `utilisateur_validateur`, `demande_compte` (`ALTER TABLE … SET SCHEMA` : données, index,
  contraintes, séquences identity et droits suivent ; les 32 FK entrantes et la vue
  `v_historique_mouvement_stock` restent valides — vérifié sur la PREPROD le 26/11/2026) ;
- ajoute `service.actif` (pour process, D3) ;
- donne au login `<base>_app` (script 05) l'usage du schéma et les droits par défaut sur ses
  futures tables ; s'arrête si le login n'existe pas ;
- règle `search_path = public, referentiel` **au niveau de la base** (toutes les connexions,
  pgAdmin compris), filet de sécurité pour le SQL écrit à la main ;
- affiche 4 contrôles, et contient en commentaire le bloc de retour arrière.

Les droits de lecture du login process sur `referentiel` sont donnés en phase 2, quand process
arrive dans la base.

Changements Java achat :

| Fichier | Changement |
|---|---|
| `model/Referentiel.java` (nouveau) | constante `SCHEMA = "referentiel"` |
| `model/utilisateur/Utilisateur.java` | `@Table(…, schema = Referentiel.SCHEMA)` ; idem sur `@JoinTable utilisateur_validateur` ; `utilisateur_role` reste dans public |
| `model/utilisateur/Poste.java`, `Pdp.java`, `DemandeCompte.java`, `model/util/Service.java` | `@Table(…, schema = Referentiel.SCHEMA)` |

`service.actif` n'est pas encore mappé côté achat (valeur par défaut `true`) : à ajouter quand
l'écran Organisation devra désactiver une direction.

⚠️ **Script et code vont ensemble** : la nouvelle version ne démarre pas tant que le script n'a pas
été exécuté (`Schema-validation: missing table [referentiel.demande_compte]`), et l'ancienne version
ne démarre plus après.

**Déroulé**

1. Répétition : créer `ACHAT_APPROV_MIGR` (phase 0), y exécuter `05` puis `06`, lire les contrôles.
2. Vérifier le code contre cette base :
   ```powershell
   $env:SPRING_DATASOURCE_URL = 'jdbc:postgresql://10.25.10.151:5432/ACHAT_APPROV_MIGR'
   .\mvnw test "-Dtest=SchemaValidationTest" "-Dschema.validation=true"
   ```
   (le login `achat_approv_preprod_app` n'a pas accès à `ACHAT_APPROV_MIGR` : exécuter `05` sur
   `ACHAT_APPROV_MIGR` crée `achat_approv_migr_app`, à mettre temporairement dans
   `application-secret.properties`, ou lancer le test avec `SPRING_DATASOURCE_USERNAME` /
   `SPRING_DATASOURCE_PASSWORD`.)
3. Démarrer l'appli sur `ACHAT_APPROV_MIGR` et tester : connexion AD et connexion de test, écran
   Organisation, création d'un compte par `DemandeCompte` (maker puis checker), création et
   validation d'une demande, bon de commande, inventaire, historique des mouvements de stock.
4. PREPROD : arrêter l'appli, exécuter `06`, déployer la nouvelle version, refaire le tour du point 3.

La phase 1 peut partir en PROD **seule** : l'appli achat fonctionne comme avant, process n'est pas
encore touchée.

### Phase 2 — Import de process dans la même base (SQL)

Script `scriptSQL/PROD/07_import_process.sql`, après restauration du dump process dans un schéma
`process` de la base commune (`pg_dump -n public PROCESS_xxx`, restauration, puis
`ALTER SCHEMA public RENAME TO process` sur une base de transit, ou `sed` du nom de schéma) :

1. **Directions** : table de correspondance `process.map_direction(id_direction_process, id_service)`,
   remplie par `acronyme = code`, complétée à la main, et insertion dans `referentiel.service` des
   directions absentes côté achat. Contrôle : aucune direction process sans correspondance.
2. **Utilisateurs** : table `process.map_utilisateur(id_process, id_referentiel)` remplie par
   `lower(trim(mail))`. Les utilisateurs process absents de l'achat sont insérés dans
   `referentiel.utilisateur` (nom, prenom, mail, actif, id_service via `map_direction`).
   Contrôle : nombre d'utilisateurs process = nombre de lignes de `map_utilisateur`, et liste des
   homonymes à mails différents (doublons possibles à vérifier avec le métier).
3. **Réécriture des FK** de `evaluation`, `procedure`, `demande_utilisateur`, `utilisateur_role`
   avec les tables de correspondance (supprimer d'abord les anciennes FK, mettre à jour, puis
   recréer les FK vers `referentiel.utilisateur(id_utilisateur)` et `referentiel.service(id_service)`).
4. `DROP TABLE process.utilisateur, process.direction;`
5. Droits : `app_process` reçoit lecture + écriture sur toutes les tables et séquences de
   `process` (`GRANT USAGE ON SCHEMA process`, `GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES …`,
   `GRANT USAGE, SELECT ON ALL SEQUENCES …`), puis
   `ALTER ROLE app_process SET search_path = process, referentiel;`

Garder les tables `map_*` quelques semaines pour pouvoir tracer.

### Phase 3 — Adaptation de l'appli process (Java)

| Élément | Changement |
|---|---|
| `application.properties` | URL de la base commune, login `app_process`, `spring.jpa.properties.hibernate.default_schema=process`, `ddl-auto=validate` |
| `Utilisateur` | `@Table(name = "utilisateur", schema = "referentiel")`, `@JoinColumn(name = "id_service")` pour la direction, `@JoinTable(name = "utilisateur_role")` dans `process` ; colonnes inutiles pour process non mappées |
| `Direction` | `@Table(name = "service", schema = "referentiel")`, `code` → `@Column(name = "acronyme")` |
| `DemandeUtilisateur` / service associé | les demandes de **création** et de modification d'identité disparaissent ; restent les demandes d'**attribution ou retrait de rôles** sur un utilisateur existant (recherche dans le référentiel) |
| `DirectionController` / `DirectionService` | lecture seule (selon D3) |
| Écrans | « Créer un utilisateur » remplacé par « Donner accès à l'application » ; message renvoyant vers l'appli achat pour créer la personne |
| `scriptSQL/init_utilisateur_admin.sql` | réécrit : il n'insère plus d'utilisateur, il donne seulement le rôle ADMIN à un utilisateur existant |

Les deux applis ont des versions de Spring Boot différentes (3.3.5 et 4.2.0, donc Hibernate 6 et 7) :
**ne pas partager de JAR d'entités** pour l'instant ; chaque appli garde sa propre classe
`Utilisateur`, mappée sur la même table.

### Phase 4 — Mise en production

| Étape | Action | Arrêt |
|---|---|---|
| 1 | Sauvegarde des deux bases PROD | — |
| 2 | Exécuter `06_schema_referentiel.sql`, déployer l'achat (phase 1), tests de fumée | achat seule, ~15 min |
| 3 | Quelques jours d'observation | — |
| 4 | Arrêter process, exécuter `07_import_process.sql`, lire les contrôles | process seule |
| 5 | Déployer process (phase 3), tests de fumée | process seule |
| 6 | Mettre à jour `scriptSQL/PROD/README.md` | — |

**Retour arrière** : phase 1 → `ALTER TABLE referentiel.x SET SCHEMA public` et redéploiement de la
version précédente. Phase 2 → restauration de la sauvegarde de la base process (l'ancienne base
`PROCESS_xxx` n'est supprimée qu'après validation).

## 6. Risques

| Risque | Parade |
|---|---|
| Hibernate `update` recrée des tables ou modifie le référentiel | `ddl-auto=validate` dans les deux applis (phase 0) |
| Même personne avec deux mails différents dans les deux applis | Liste des homonymes produite par le script 07, arbitrage métier avant la PROD |
| Directions sans correspondance | Table `map_direction` validée à la main ; le script s'arrête s'il en reste |
| Process écrit encore dans le référentiel par un chemin oublié | Login `app_process` en `SELECT` seul : l'erreur apparaît immédiatement |
| Script SQL qui recrée une vue sans préfixe de schéma | `search_path` des logins + règle « toujours écrire `referentiel.` » dans le README |
| Démarrage d'une appli sur la PREPROD pendant les répétitions | Répéter sur `ACHAT_APPROV_MIGR` |

## 7. Plus tard (hors périmètre)

- Flyway dans les deux applis pour versionner les scripts au lieu de `scriptSQL/PROD`.
- Renommer `service` en `direction` dans le référentiel (D5).
- Si d'autres applis arrivent (afg-rh…) : exposer le référentiel par une API, ou passer à un
  fournisseur d'identité (Keycloak fédéré sur l'AD) avec les rôles par application.
