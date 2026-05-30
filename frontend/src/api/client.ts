import axios from 'axios';
import type { Agent, Order, Suggestion, UpdateSuggestionRequest, AgentStatus, CreateOrderRequest } from '@/types';

const api = axios.create({
  baseURL: '/api',
  headers: {
    'Content-Type': 'application/json',
  },
});

export async function getOrders(status?: string): Promise<Order[]> {
  const params = status ? { status } : {};
  const response = await api.get<Order[]>('/orders', { params });
  return response.data;
}

export async function getAgents(): Promise<Agent[]> {
  const response = await api.get<Agent[]>('/agents');
  return response.data;
}

export async function getSuggestions(status?: string): Promise<Suggestion[]> {
  const params = status ? { status } : {};
  const response = await api.get<Suggestion[]>('/suggestions', { params });
  return response.data;
}

export async function getSuggestionById(id: number): Promise<Suggestion> {
  const response = await api.get<Suggestion>(`/suggestions/${id}`);
  return response.data;
}

export async function updateSuggestionStatus(
  id: number,
  request: UpdateSuggestionRequest
): Promise<unknown> {
  const response = await api.patch(`/suggestions/${id}`, request);
  return response.data;
}

export async function updateAgentStatus(
  agentId: string,
  status: AgentStatus
): Promise<Agent> {
  const response = await api.patch<Agent>(`/agents/${agentId}/status`, { status });
  return response.data;
}

export async function requestSuggestion(
  orderId: string,
  reason: string = 'MANUAL_REQUEST'
): Promise<unknown> {
  const response = await api.post('/suggestions/start', null, {
    params: { orderId, reason },
  });
  return response.data;
}

export async function createOrder(request: CreateOrderRequest): Promise<Order> {
  const response = await api.post<Order>('/orders', request);
  return response.data;
}

export default api;
