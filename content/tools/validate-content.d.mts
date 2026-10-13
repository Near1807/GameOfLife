// Types for the frontend tests that import the validator.
export function validateContent(root: string): { errors: string[]; checked: number };
export function checkSchema(value: unknown, schema: unknown, where: string): string[];
