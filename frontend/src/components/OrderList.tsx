import { useEffect, useState, useCallback } from 'react';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { SuggestionCard } from './SuggestionCard';
import { getOrders, getSuggestions, requestSuggestion } from '@/api/client';
import type { Suggestion, OrderWithSuggestion } from '@/types';

interface OrderListProps {
  onRefresh?: () => void;
}

export function OrderList({ onRefresh }: OrderListProps) {
  const [orders, setOrders] = useState<OrderWithSuggestion[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [requestingOrderId, setRequestingOrderId] = useState<string | null>(null);

  const fetchData = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const [ordersData, suggestionsData] = await Promise.all([
        getOrders('REASSIGNMENT_PENDING'),
        getSuggestions('PENDING'),
      ]);

      const suggestionsByOrder = suggestionsData.reduce<Record<string, Suggestion>>(
        (acc, suggestion) => {
          if (!acc[suggestion.orderId] || suggestion.id > acc[suggestion.orderId].id) {
            acc[suggestion.orderId] = suggestion;
          }
          return acc;
        },
        {}
      );

      const ordersWithSuggestions: OrderWithSuggestion[] = ordersData.map((order) => ({
        ...order,
        pendingSuggestion: suggestionsByOrder[order.id],
      }));

      setOrders(ordersWithSuggestions);
    } catch (err) {
      setError('Failed to load orders');
      console.error(err);
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  const handleRequestSuggestion = async (orderId: string) => {
    setRequestingOrderId(orderId);
    try {
      await requestSuggestion(orderId, 'MANUAL_REQUEST');
      setTimeout(fetchData, 1000);
    } catch (err) {
      console.error('Failed to request suggestion:', err);
    } finally {
      setRequestingOrderId(null);
    }
  };

  const handleSuggestionUpdate = () => {
    fetchData();
    onRefresh?.();
  };

  if (isLoading) {
    return (
      <div className="flex items-center justify-center p-8">
        <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary"></div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="text-center p-8 text-destructive">
        {error}
        <Button variant="outline" className="ml-4" onClick={fetchData}>
          Retry
        </Button>
      </div>
    );
  }

  if (orders.length === 0) {
    return (
      <div className="text-center p-8 text-muted-foreground">
        No orders pending reassignment
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <div className="flex justify-between items-center">
        <h2 className="text-lg font-semibold">
          Pending Reassignments ({orders.length})
        </h2>
        <Button variant="outline" size="sm" onClick={fetchData}>
          Refresh
        </Button>
      </div>

      <Table>
        <TableHeader>
          <TableRow>
            <TableHead className="w-[100px]">Order ID</TableHead>
            <TableHead>Description</TableHead>
            <TableHead>Current Agent</TableHead>
            <TableHead>Status</TableHead>
            <TableHead className="w-[350px]">Suggestion</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {orders.map((order) => (
            <TableRow key={order.id}>
              <TableCell className="font-mono text-sm">{order.id}</TableCell>
              <TableCell className="max-w-[200px] truncate">{order.description}</TableCell>
              <TableCell className="text-sm">{order.assignedAgentName || '-'}</TableCell>
              <TableCell>
                <Badge variant="secondary">{order.status}</Badge>
              </TableCell>
              <TableCell>
                {order.pendingSuggestion ? (
                  <SuggestionCard
                    suggestion={order.pendingSuggestion}
                    onUpdate={handleSuggestionUpdate}
                  />
                ) : (
                  <Button
                    size="sm"
                    variant="outline"
                    onClick={() => handleRequestSuggestion(order.id)}
                    disabled={requestingOrderId === order.id}
                  >
                    {requestingOrderId === order.id ? 'Requesting...' : 'Request Suggestion'}
                  </Button>
                )}
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  );
}
