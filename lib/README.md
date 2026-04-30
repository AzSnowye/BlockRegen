# Folder `lib/`

Folder ini digunakan untuk menyimpan dependency lokal yang tidak tersedia di Maven publik.

## ModelEngine

1. **Download** `ModelEngine.jar` dari:
   - SpigotMC: https://www.spigotmc.org/resources/model-engine%E2%80%94ultimate-entity-model-manager-1-16-5-1-21-4.79477/
   - Atau ambil dari server Minecraft kamu di folder `plugins/`

2. **Letakkan** file JAR di folder ini dengan nama persis `ModelEngine.jar`:
   ```
   BlockRegen/
   └── lib/
       └── ModelEngine.jar   <-- file ini
   ```

3. **Install ke Maven lokal** (hanya perlu sekali):
   ```bash
   mvn install:install-file -Dfile=lib/ModelEngine.jar -DgroupId=com.ticxo.modelengine -DartifactId=api -Dversion=R4.0.7 -Dpackaging=jar
   ```
   
   Atau ubah scope di `pom.xml` dari `system` ke `provided` setelah install ke local Maven.

4. **Build plugin** seperti biasa:
   ```bash
   mvn clean package
   ```

> **Note:** File `*.jar` di folder ini di-gitignore secara otomatis.
