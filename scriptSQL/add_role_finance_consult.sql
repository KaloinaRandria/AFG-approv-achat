-- Ajout du rôle FINANCE_CONSULT pour la consultation seule de la vue finance
INSERT INTO role (role) 
SELECT 'FINANCE_CONSULT'
WHERE NOT EXISTS (
    SELECT 1 FROM role WHERE role = 'FINANCE_CONSULT'
);
