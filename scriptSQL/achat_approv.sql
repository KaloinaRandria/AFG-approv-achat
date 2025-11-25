ALTER TABLE utilisateur
    ADD CONSTRAINT chk_superieur CHECK (id_superieur IS NULL OR id_superieur <> id_utilisateur);
