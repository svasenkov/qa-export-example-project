import { apiUrl } from './appBase';

export interface HealthResponse {
  status: string;
  service: string;
}

export interface Item {
  id: number;
  name: string;
  description: string;
}

export interface ItemsResponse {
  items: Item[];
  source?: string;
}

export async function fetchHealth(): Promise<HealthResponse> {
  const response = await fetch(apiUrl('/health'));
  if (!response.ok) {
    throw new Error(`HTTP ${response.status}`);
  }
  return (await response.json()) as HealthResponse;
}

export async function fetchItems(): Promise<ItemsResponse> {
  const response = await fetch(apiUrl('/items'));
  if (!response.ok) {
    throw new Error(`HTTP ${response.status}`);
  }
  return (await response.json()) as ItemsResponse;
}
