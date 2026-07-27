-- As duas extensões são criadas na migração (e não num script de init do
-- contentor) para que o Testcontainers e o docker compose obtenham
-- exatamente o mesmo esquema sem duplicação de configuração.
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS unaccent;
