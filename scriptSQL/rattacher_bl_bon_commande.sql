-- Rattachement des bons de livraison aux bons de commande.
-- Hibernate (ddl-auto=update) crée les colonnes au démarrage ; ce script les crée aussi
-- pour un déploiement sans mise à jour automatique, puis reprend les données existantes.

ALTER TABLE bon_livraison_mere ADD COLUMN IF NOT EXISTS id_bc_mere VARCHAR(255)
    REFERENCES bon_commande_mere (id_bc_mere);
ALTER TABLE bon_livraison_fille ADD COLUMN IF NOT EXISTS id_bc_fille INTEGER
    REFERENCES bon_commande_fille (id_bc_fille);

ALTER TABLE bon_commande_mere ADD COLUMN IF NOT EXISTS statut_reception VARCHAR(255);
ALTER TABLE bon_commande_mere ADD COLUMN IF NOT EXISTS motif_cloture_reception TEXT;
ALTER TABLE bon_commande_mere ADD COLUMN IF NOT EXISTS id_cloture_reception_par INTEGER
    REFERENCES utilisateur (id_utilisateur);
ALTER TABLE bon_commande_mere ADD COLUMN IF NOT EXISTS date_cloture_reception TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_bl_mere_bc ON bon_livraison_mere (id_bc_mere);
CREATE INDEX IF NOT EXISTS idx_bl_fille_bc ON bon_livraison_fille (id_bc_fille);

-- Bons existants : rien n'a encore été reçu par le nouveau circuit
UPDATE bon_commande_mere SET statut_reception = 'NON_RECU' WHERE statut_reception IS NULL;

-- Reste à recevoir recalculé sur la quantité commandée
UPDATE bon_commande_fille
SET quantite_restante = GREATEST(quantite_commandee - COALESCE(quantite_recue, 0), 0);
