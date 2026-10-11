-- CreateTable
CREATE TABLE "license_keys" (
    "id" UUID NOT NULL,
    "key" TEXT NOT NULL,
    "planDays" INTEGER NOT NULL DEFAULT 365,
    "boundToShopId" UUID,
    "issuedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "boundAt" TIMESTAMP(3),

    CONSTRAINT "license_keys_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE UNIQUE INDEX "license_keys_key_key" ON "license_keys"("key");
