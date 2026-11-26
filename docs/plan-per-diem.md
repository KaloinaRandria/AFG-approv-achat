# Plan : demande de Per Diem et ordre de mission

> Statut : **proposition**, rédigée le 26/11/2026. Les points marqués **[à confirmer]** reprennent
> les questions ouvertes du §9 ; la valeur indiquée est la recommandation retenue par défaut.
> Modèle de référence : ordre de mission n° 093/26 (mission à Tuléar, 25 au 27/10/2026).

## 1. Objectif

Gérer dans l'application, de bout en bout, une mission d'un collaborateur :

1. saisie de la mission (missionnaire, destination, objet, itinéraire, transport) ;
2. calcul du per diem à partir d'un **barème** et du **nombre de jours / nuitées** ;
3. circuit de validation **RH → DRH → Contrôleur de gestion → S.G.** ;
4. émission de l'**ordre de mission** numéroté `NNN/AA` (ex. `093/26`), imprimable ;
5. paiement du per diem par la Finance, puis régularisation au retour.

## 2. Choix d'architecture

### 2.1 Module à part, pas un nouveau `TypeDemande`

Le circuit des demandes d'achat (`EtapeWorkflow`) est un circuit fixe
N+1 → Moyens Généraux → Contrôleur → D.F.C. → S.G., que `TypeDemande` peut seulement
**raccourcir**. Le per diem a besoin :

- d'étapes absentes du circuit achat (RH, DRH) ;
- d'une étape où l'on **saisit** une donnée (le montant), pas seulement où l'on approuve ;
- de données propres (itinéraire, nuitées, barème, numéro d'OM) sans rapport avec `DemandeMere`
  (articles, bon de commande, stock).

On crée donc un module `perdiem` avec ses propres entités et son propre enum d'étapes,
**sur le même modèle** que l'existant :

| Existant (achat) | Équivalent per diem |
|---|---|
| `EtapeWorkflow` | `EtapeMission` |
| `ValidationDemande` | `ValidationMission` |
| `DemandePieceJointe` | `MissionPieceJointe` |
| `PaiementDirect` | `PaiementMission` (ou réutilisation, cf. §6) |
| `compteur_bc` + `IdGenerator` | `compteur_ordre_mission` |
| `bon-commande-fiche.html` (+ `@media print`) | `ordre-mission-impression.html` |

Ce qui est réutilisé tel quel : `Utilisateur`, `Poste`, `Service` (direction), `Devise`,
`CentreBudgetaire` / ligne budgétaire, `EmailSenderService`, `Transport`.

### 2.2 Application cible **[à confirmer]**

Par défaut : **appli achat** (ce dépôt). Elle porte déjà le référentiel utilisateurs, la Finance,
les devises, les lignes budgétaires et les mails. Si le per diem doit aller dans **AFG-process**,
le modèle ci-dessous reste valable ; seuls les schémas (`process` au lieu de `public`) et le login
applicatif changent.

## 3. Rôles

Logique des rôles génériques déjà en place (`roles_maker_checker_generiques.sql`) : le domaine vient
du rôle métier, l'action vient de MAKER / CHECKER.

| Rôles portés | Profil | Peut |
|---|---|---|
| (aucun rôle particulier) | Collaborateur / demandeur | créer une mission pour lui-même, suivre ses missions |
| `RH` + `MAKER` | Gestionnaire RH | créer une mission pour un tiers, appliquer le barème, saisir / ajuster le per diem, gérer le barème (proposition) |
| `RH` + `CHECKER` | DRH | valider ou refuser, valider les modifications du barème |
| `CONTROLEUR` | Contrôleur de gestion | valider (contrôle budgétaire), refuser |
| `SG` | Secrétaire Général | valider (émission de l'OM), refuser |
| `FINANCE` | Finance | marquer l'avance et le solde comme payés |
| `RH` (seul) | Consultation RH | consulter toutes les missions, tableau de bord |

Règles maker / checker : le DRH qui valide ne peut pas être la personne qui a saisi le montant,
ni le missionnaire lui-même (même contrôle que pour `DemandeCompte`).

Script : `scriptSQL/ajout_role_rh.sql` (insère `RH` dans `role`, rejouable), puis
`scriptSQL/PROD/0x_role_rh.sql`.

## 4. Circuit de validation

```
BROUILLON ─▶ SOUMISE ─▶ [N+1] ─▶ SAISIE_RH ─▶ VALIDATION_DRH ─▶ VALIDATION_CG ─▶ VALIDATION_SG ─▶ EMISE
   │            demandeur  (opt.)   RH MAKER      RH CHECKER        CONTROLEUR        SG              │
   │                                                                                                   ▼
   └────────────── REFUSEE (à toute étape, commentaire obligatoire) ◀──────────────     PAYEE ─▶ CLOTUREE
                                                                                      (Finance)  (régularisation)
```

| Étape | Qui agit | Action | Suivante |
|---|---|---|---|
| `BROUILLON` | demandeur | saisit, modifie, soumet | `SOUMISE` |
| `SOUMISE` | N+1 du missionnaire **[à confirmer : étape N+1 oui/non]** | approuve / refuse | `SAISIE_RH` |
| `SAISIE_RH` | RH MAKER | applique le barème, ajuste, renvoie au demandeur pour correction ou transmet | `VALIDATION_DRH` |
| `VALIDATION_DRH` | RH CHECKER | approuve / refuse / **renvoie au RH MAKER** | `VALIDATION_CG` |
| `VALIDATION_CG` | CONTROLEUR | contrôle budget, approuve / refuse | `VALIDATION_SG` |
| `VALIDATION_SG` | SG | approuve / refuse | `EMISE` (numéro attribué, OM imprimable) |
| `EMISE` | Finance | paie l'avance | `PAYEE` |
| `PAYEE` | RH MAKER | saisit le décompte réel au retour (cf. §7) | `CLOTUREE` |

- « Renvoyer » (DRH → RH MAKER, RH MAKER → demandeur) n'est pas un refus : la mission revient à
  l'étape précédente avec un commentaire, comme une correction.
- Chaque action crée une ligne `validation_mission` (étape, décision, acteur, date, commentaire) :
  c'est l'historique affiché sur la fiche.
- Mail au(x) acteur(s) de l'étape suivante, et au demandeur à l'émission / au refus
  (`EmailSenderService` + gabarits Thymeleaf dans `templates/email`).

## 5. Modèle de données

### 5.1 `mission` (la demande et l'ordre de mission)

| Colonne | Type | Remarque |
|---|---|---|
| `id_mission` | serial PK | |
| `numero_om` | varchar(10), unique, null | `093/26`, attribué à l'étape S.G. |
| `annee_om` / `sequence_om` | int, int | pour trier et contrôler l'unicité (`UNIQUE(annee_om, sequence_om)`) |
| `id_missionnaire` | FK `utilisateur` | |
| `fonction_missionnaire` | varchar | **copiée** à la création (le poste peut changer ensuite ; l'OM doit rester fidèle) |
| `id_categorie_personnel` | FK `categorie_personnel` | déduite du poste, modifiable par le RH |
| `id_demandeur` | FK `utilisateur` | qui a saisi (lui-même ou un RH) |
| `ville_depart` | varchar | défaut « Antananarivo » |
| `destination` | varchar | ex. « TULEAR, MADAGASCAR » |
| `id_zone` | FK `zone_mission` | détermine la ligne du barème |
| `objet` | text | « Mission d'aménagement de l'agence de Tuléar » |
| `date_depart_aller` / `date_arrivee_aller` | date (+ heure optionnelle) | |
| `date_depart_retour` / `date_arrivee_retour` | date (+ heure optionnelle) | |
| `id_transport` | FK `transport` | avion, voiture de service, taxi-brousse… (table existante) |
| `hebergement_pris_en_charge` | boolean | hôtel réservé et payé par AFG → pas d'indemnité d'hébergement |
| `nb_jours` / `nb_nuitees` | int | calculés, figés à la saisie RH, ajustables |
| `montant_per_diem` | numeric | total figé (somme des lignes §5.4) |
| `id_devise` | FK `devise` | MGA par défaut |
| `id_centre_budgetaire` / ligne budgétaire | FK | imputation, contrôlée par le CG |
| `statut` | varchar (enum `EtapeMission`) | |
| `date_ordre` | date | date « Antananarivo, le … » imprimée, = date de validation S.G. |
| `date_creation`, `date_soumission` | timestamp | |

### 5.2 `bareme_per_diem` (versionné)

| Colonne | Remarque |
|---|---|
| `id_bareme` | PK |
| `id_categorie_personnel` | Directeur, Cadre, Agent… |
| `id_zone` | Antananarivo, chef-lieu de province, autre ville, étranger (Afrique, Europe…) |
| `type_indemnite` | `REPAS`, `HEBERGEMENT`, `FORFAIT_JOURNALIER`, `DEPLACEMENT_LOCAL` |
| `unite` | `JOUR` ou `NUITEE` |
| `montant`, `id_devise` | |
| `date_effet`, `date_fin` | `date_fin` null = en vigueur |
| `statut` | `PROPOSE` → `VALIDE` (RH MAKER propose, DRH valide) |

Règle : la ligne applicable est celle dont `date_effet ≤ date de départ < date_fin`. On ne modifie
jamais une ligne validée ; on la clôt (`date_fin`) et on en crée une nouvelle. Les missions
déjà calculées gardent leur montant (§5.4).

Tables de référence : `categorie_personnel` (code, libellé, ordre, actif) et `zone_mission`
(code, libellé, pays, actif). Lien `poste → categorie_personnel` pour proposer la catégorie
automatiquement.

### 5.3 `mission_etape` (itinéraire multi-villes, optionnel en V1)

Une ligne par tronçon (ville départ, ville arrivée, date, transport). En V1, les quatre dates
de `mission` suffisent pour le modèle 093/26 ; la table permet ensuite Tana → Tuléar →
Fianarantsoa → Tana sans changer le reste.

### 5.4 `mission_ligne_per_diem` (détail figé du calcul)

| Colonne | Remarque |
|---|---|
| `id_mission` | |
| `type_indemnite`, `unite` | copiés du barème |
| `quantite` | nb de jours ou de nuitées |
| `montant_unitaire` | **copié** du barème au moment du calcul |
| `id_bareme` | traçabilité de la ligne d'origine |
| `montant` | quantité × montant unitaire |
| `ajuste` / `motif_ajustement` | si le RH MAKER a modifié la valeur proposée |

### 5.5 Autres tables

- `validation_mission` : id, id_mission, étape, décision (`APPROUVE`, `REFUSE`, `RENVOYE`),
  acteur, date, commentaire.
- `mission_piece_jointe` : billets, réservation d'hôtel, rapport de mission, attestation de présence.
- `compteur_ordre_mission(annee, dernier_numero)` : même mécanisme que `compteur_bc`
  (verrou + incrément dans la transaction de validation S.G.).
- `paiement_mission` : cf. §6.

## 6. Calculs

### 6.1 Jours et nuitées

- `nb_jours = date_arrivee_retour − date_depart_aller + 1`
- `nb_nuitees = date_arrivee_retour − date_depart_aller` (0 pour un aller-retour dans la journée)
- Exemple 093/26 : départ 25/10, retour 27/10 → **3 jours, 2 nuitées**.
- Option **[à confirmer]** : demi-journée selon les heures de départ / retour (ex. départ après
  14 h → pas de repas du midi le premier jour). Non retenu en V1 sauf si la note RH le prévoit.

### 6.2 Montant

Pour chaque ligne du barème applicable (catégorie × zone × date de départ) :
`montant = quantité (jours ou nuitées) × montant unitaire`.
Si `hebergement_pris_en_charge = true`, la ligne `HEBERGEMENT` est omise.
Le total est la somme des lignes ; le RH MAKER peut ajuster une ligne avec un motif obligatoire,
visible du DRH.

### 6.3 Numéro d'ordre de mission

- Attribué **à la validation S.G.** **[à confirmer]** : pas de trou dans la numérotation quand une
  mission est refusée.
- Format `String.format("%03d/%02d", sequence, annee % 100)` → `093/26`. Repart à 001 au 1er janvier
  (année de la date de validation S.G.).

### 6.4 Paiement

Deux options :

- **A (recommandée)** : table `paiement_mission` dédiée, calquée sur `PaiementDirect`
  (`A_TRANSMETTRE` → `TRANSMIS` → `PAYE`, payé par, date, mode de paiement, référence), avec deux
  lignes possibles : **avance** et **solde / reversement** (cf. §7).
- B : rendre `PaiementDirect.demande` nullable et ajouter `id_mission`. Moins de code, mais mélange
  deux circuits dans la même table.

## 7. Régularisation au retour

Après la mission, le RH MAKER saisit les dates réelles de retour (ou la confirmation qu'elles sont
inchangées) et joint le rapport / l'attestation de présence :

- mission conforme → `CLOTUREE`, aucun mouvement ;
- mission **prolongée** → complément calculé avec le même barème, validé par le DRH, payé par la Finance ;
- mission **écourtée ou annulée** → trop-perçu à reverser, suivi jusqu'au remboursement.

Une mission **annulée avant le départ** passe à `ANNULEE` ; son numéro d'OM reste attribué
(traçabilité) et l'avance éventuelle est à reverser.

## 8. Écrans

| Écran | Accès | Contenu |
|---|---|---|
| Mes missions | tous | liste + bouton « Nouvelle mission » |
| Saisie mission | demandeur, RH MAKER | missionnaire (lui-même ou, pour le RH, recherche), destination, zone, objet, 4 dates, transport, hébergement pris en charge, imputation |
| Fiche mission | acteurs + missionnaire | données, détail du calcul, frise du circuit, historique, pièces jointes, boutons de l'étape en cours |
| Missions à traiter | RH, DRH, CG, SG, Finance | missions en attente de l'utilisateur connecté (badge dans le menu) |
| Ordre de mission (impression) | tous, une fois `EMISE` | reproduction du modèle 093/26 : logo, « Antananarivo, le … », cadre « ORDRE DE MISSION N° … », civilité + nom + fonction, destination, objet, 4 dates, transport, mentions, signataire S.G., pied de page légal ; `@media print`, bouton Imprimer (comme le bon de commande) |
| Barème | RH (consultation), RH MAKER (proposition), RH CHECKER (validation) | grille catégorie × zone × type, historique des versions |
| Référentiels | RH MAKER | catégories de personnel, zones, lien poste → catégorie |
| Tableau de bord RH | RH, CG, SG | missions en cours, coût par direction / mois / zone |

**Signature de l'OM [à confirmer]** : par défaut, nom du S.G. + mention « Validé électroniquement le
… » + QR code renvoyant à la fiche (vérification d'authenticité). Une signature / un cachet scanné
reste possible (image stockée, affichée seulement quand le statut est `EMISE`).

## 9. Questions ouvertes

| # | Question | Défaut retenu dans ce plan |
|---|---|---|
| Q1 | Appli achat ou AFG-process ? | Appli achat |
| Q2 | Qui crée la mission ? | Le missionnaire **et** le RH MAKER (pour un tiers) |
| Q3 | Validation du N+1 avant le RH ? | Oui (étape `SOUMISE`), désactivable |
| Q4 | Quand attribuer le numéro d'OM ? | À la validation S.G. |
| Q5 | Grille du barème (catégories × zones × montants) | À fournir par la DRH |
| Q6 | Signature de l'OM | Mention électronique + QR code |
| Q7 | Avance : 100 % ou un pourcentage ? | 100 % avant le départ, régularisation au retour |
| Q8 | Demi-journées selon les heures ? | Non en V1 |
| Q9 | Plusieurs missionnaires pour une même mission ? | V2 : une mission « groupe » génère un OM par personne |
| Q10 | Délai minimal de soumission avant le départ ? | Alerte (non bloquante) à moins de 3 jours ouvrés |

## 10. Contrôles

- date de retour ≥ date de départ ; dates cohérentes entre aller et retour ;
- pas de chevauchement avec une autre mission non refusée du même missionnaire ;
- une ligne de barème `VALIDE` doit exister pour la catégorie, la zone et la date, sinon saisie RH bloquée avec un message clair ;
- séparation des tâches : le DRH ne valide ni sa propre mission, ni un montant qu'il a lui-même saisi ;
- le CG voit le disponible de la ligne budgétaire imputée (si l'information existe déjà côté achat).

## 11. Livraison par lots

| Lot | Contenu | Livrable vérifiable |
|---|---|---|
| **0** | Rôle `RH` (script rejouable + PROD), menu « Missions » visible selon les rôles | un utilisateur RH voit le menu |
| **1** | Référentiels : `categorie_personnel`, `zone_mission`, `bareme_per_diem` versionné + écrans barème (maker / checker) | barème saisi et validé en PREPROD |
| **2** | `mission` + saisie + brouillon / soumission + calcul jours / nuitées / montant (`mission_ligne_per_diem`) | une mission calculée comme l'exemple 093/26 |
| **3** | Circuit `EtapeMission` + `validation_mission` + écrans « à traiter » + mails | une mission va de la soumission à `EMISE` |
| **4** | Numérotation `compteur_ordre_mission` + gabarit d'impression de l'OM (modèle fourni) | OM `001/26` imprimé, conforme au modèle |
| **5** | Paiement (avance) par la Finance + régularisation au retour + annulation | mission `CLOTUREE` |
| **6** | Tableau de bord RH, itinéraire multi-villes, missions groupées | — |

Pour chaque lot touchant la base : script SQL dans `scriptSQL/` (PREPROD) et `scriptSQL/PROD/`
(numéroté), à cause de `ddl-auto=validate` ; ajout des nouvelles entités à `SchemaValidationTest`.

## 12. Hors périmètre (V1)

- réservation des billets / de l'hôtel dans l'application ;
- remboursement des frais réels sur justificatifs (notes de frais) : à traiter, si besoin, en demande « Autre » existante ;
- intégration comptable automatique.
