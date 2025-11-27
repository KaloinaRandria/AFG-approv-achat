ALTER TABLE utilisateur
    ADD CONSTRAINT chk_superieur CHECK (id_superieur IS NULL OR id_superieur <> id_utilisateur);

select
    u.id_utilisateur as id_utilisateur,
    u.nom as nom_utilisateur,
    u.prenom as prenom_utilisateur,
    u.mail as mail_utilisateur,
    r.role as role_utilisateur
from utilisateur_role
join public.role r on r.id_role = utilisateur_role.id_role
join public.utilisateur u on u.id_utilisateur = utilisateur_role.id_utilisateur;

select * from article_id_article_seq;