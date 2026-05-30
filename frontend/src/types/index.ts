export type AgentStatus = 'AVAILABLE' | 'BUSY' | 'OFFLINE';
export type OrderStatus = 'PENDING' | 'ASSIGNED' | 'REASSIGNMENT_PENDING' | 'REASSIGNED' | 'DELIVERED' | 'CANCELLED';
export type SuggestionStatus = 'PENDING' | 'ACCEPTED' | 'REJECTED' | 'EXPIRED';
export type TriggerReason = 'INITIAL' | 'AGENT_OFFLINE' | 'MANUAL_REQUEST' | 'SUGGESTION_REJECTED';
export type StreamStatus = 'PROCESSING' | 'COMPLETED' | 'FAILED';

export interface Agent {
  id: string;
  name: string;
  status: AgentStatus;
  activeOrderCount: number;
  currentZone?: string;
  maxCapacity?: number;
}

export interface Order {
  id: string;
  description: string;
  assignedAgentId?: string;
  assignedAgentName?: string;
  status: OrderStatus;
  createdAt: string;
  weightClass?: string;
  pickupZone?: string;
  dropoffZone?: string;
  slaDeadline?: string;
}

export interface Suggestion {
  id: number;
  orderId: string;
  orderDescription: string;
  currentAgentId?: string;
  currentAgentName?: string;
  recommendedAgentId: string;
  recommendedAgentName: string;
  status: SuggestionStatus;
  reasoning: string;
  confidence: number;
  triggerReason: TriggerReason;
  createdAt: string;
}

export interface StartSuggestionResponse {
  suggestionId: number;
  orderId: string;
  status: StreamStatus;
  streamUrl: string;
  reconnectUrl: string;
  eventsUrl: string;
}

export interface OrderWithSuggestion extends Order {
  pendingSuggestion?: Suggestion;
}

export interface UpdateSuggestionRequest {
  status: 'ACCEPTED' | 'REJECTED';
  feedback?: string;
}

export interface RejectionWithRetryResponse {
  rejectedSuggestion: Suggestion;
  newSuggestion: StartSuggestionResponse;
}

export interface CreateOrderRequest {
  id: string;
  description: string;
  assignedAgentId?: string;
}
