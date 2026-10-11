# framework/

Platform-bound catalog sources. Empty: the CSV parser is pure Kotlin
(../CsvCatalogParser.kt) and Room access is wrapped in CatalogRepositoryImpl.
Sources land here if the feature gains camera scanning or file-provider
persistence of its own.
