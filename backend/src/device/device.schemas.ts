import { z } from 'zod';

export const registerDeviceSchema = z.object({
  deviceName: z.string().trim().max(120).optional(),
});
