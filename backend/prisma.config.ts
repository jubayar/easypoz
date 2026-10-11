import 'dotenv/config';
import { defineConfig } from 'prisma/config';

export default defineConfig({
  schema: 'prisma/schema.prisma',
  migrations: {
    path: 'prisma/migrations',
  },
  // `env()` throws when unset, which breaks `prisma generate` on machines
  // without a database; a blank URL is rejected only by DB-touching commands.
  datasource: {
    url: process.env.DATABASE_URL ?? '',
  },
});
