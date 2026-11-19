# Mixing MES
 
MES для производства смесей (литейные и металлургические материалы).
 
Бумажный чек-лист остаётся основным документом смены. Система превращает его фото в электронную запись о партии: QR-код определяет задание, OCR читает поля, оператор проверяет только сомнительные строки.
 
> Описание проекта дополняется. Решения и компромиссы уже описаны в [ADR](docs/adr/README.md).
 
## Стек
 
- **Бэкенд:** Java 21, Spring Boot 3, PostgreSQL, Liquibase, Kafka, MinIO (S3), Keycloak (OAuth2/JWT)
- **Интерфейс:** React, TypeScript, Vite, TanStack Query ([подробнее](frontend/README.md))
- **Тесты:** JUnit 5, Mockito, Testcontainers, Vitest, Testing Library
 
## Что уже есть
 
- рецептуры с версиями, стадиями и контрольными точками;
- производственные задания, партии, контроль дозировки по допускам;
- печатный бланк с QR-кодом, загрузка фото или PDF, проверка распознанных строк оператором;
- контроль качества по спецификациям, в том числе качественные параметры;
- прослеживаемость партий, отзыв лота сырья с карантином, склад с выдачей по FEFO;
- статистический контроль процесса (SPC: Cp, Cpk, правила Western Electric);
- журнал аудита правок, Transactional Outbox для событий.
 
OCR пока заглушка: интерфейс `OcrEngine` есть, реального распознавания нет.
 
## Запуск
 
Демо интерфейса без бэкенда (данные вымышленные):
 
```bash
cd frontend
npm install
npm run dev:demo        # http://localhost:5173
```
 
Полный стенд (нужен Docker):
 
```powershell
docker compose up -d --build
powershell -ExecutionPolicy Bypass -File scripts\seed-demo.ps1
cd frontend; npm run dev   # http://localhost:5173, вход admin.mes / admin123
```
 
Логины и пароли в `docker-compose.yml` и `docker/keycloak/mes-realm.json` тестовые, только для локального запуска.
 
## Тесты
 
```bash
mvn clean verify                 # бэкенд; интеграционному тесту нужен Docker
cd frontend && npm test          # интерфейс
```
 
