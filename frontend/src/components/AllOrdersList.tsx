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
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { getOrders, getAgents, requestSuggestion, createOrder } from '@/api/client';
import type { Order, OrderStatus, Agent } from '@/types';

interface AllOrdersListProps {
  onRefresh?: () => void;
}

function getStatusBadge(status: OrderStatus) {
  switch (status) {
    case 'PENDING':
      return <Badge variant="secondary">Pending</Badge>;
    case 'ASSIGNED':
      return <Badge className="bg-blue-500 hover:bg-blue-600">Assigned</Badge>;
    case 'REASSIGNMENT_PENDING':
      return <Badge variant="destructive">Reassignment Pending</Badge>;
    case 'REASSIGNED':
      return <Badge className="bg-green-500 hover:bg-green-600">Reassigned</Badge>;
    case 'DELIVERED':
      return <Badge className="bg-gray-500 hover:bg-gray-600">Delivered</Badge>;
    case 'CANCELLED':
      return <Badge variant="outline">Cancelled</Badge>;
    default:
      return <Badge variant="secondary">{status}</Badge>;
  }
}

export function AllOrdersList({ onRefresh }: AllOrdersListProps) {
  const [orders, setOrders] = useState<Order[]>([]);
  const [agents, setAgents] = useState<Agent[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [requestingOrderId, setRequestingOrderId] = useState<string | null>(null);

  const [isAddDialogOpen, setIsAddDialogOpen] = useState(false);
  const [newOrderId, setNewOrderId] = useState('');
  const [newOrderDescription, setNewOrderDescription] = useState('');
  const [newOrderAgentId, setNewOrderAgentId] = useState('');
  const [isCreating, setIsCreating] = useState(false);

  const fetchData = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const [ordersData, agentsData] = await Promise.all([
        getOrders(),
        getAgents(),
      ]);
      setOrders(ordersData);
      setAgents(agentsData);
    } catch (err) {
      setError('Failed to load data');
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
      setTimeout(() => {
        fetchData();
        onRefresh?.();
      }, 1500);
    } catch (err) {
      console.error('Failed to request suggestion:', err);
    } finally {
      setRequestingOrderId(null);
    }
  };

  const handleCreateOrder = async () => {
    if (!newOrderId.trim() || !newOrderDescription.trim()) {
      return;
    }
    setIsCreating(true);
    try {
      await createOrder({
        id: newOrderId.trim(),
        description: newOrderDescription.trim(),
        ...(newOrderAgentId && { assignedAgentId: newOrderAgentId }),
      });
      setIsAddDialogOpen(false);
      setNewOrderId('');
      setNewOrderDescription('');
      setNewOrderAgentId('');
      fetchData();
      onRefresh?.();
    } catch (err) {
      console.error('Failed to create order:', err);
    } finally {
      setIsCreating(false);
    }
  };

  const generateOrderId = () => {
    const nextNum = orders.length + 1;
    setNewOrderId(`ORD-${String(nextNum).padStart(3, '0')}`);
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

  const availableAgents = agents.filter(a => a.status !== 'OFFLINE');

  return (
    <div className="space-y-4">
      <div className="flex justify-between items-center">
        <h2 className="text-lg font-semibold">All Orders ({orders.length})</h2>
        <div className="flex gap-2">
          <Button size="sm" onClick={() => { generateOrderId(); setIsAddDialogOpen(true); }}>
            + Add Order
          </Button>
          <Button variant="outline" size="sm" onClick={fetchData}>
            Refresh
          </Button>
        </div>
      </div>

      {orders.length === 0 ? (
        <div className="text-center p-8 text-muted-foreground">
          No orders found. Click "Add Order" to create one.
        </div>
      ) : (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead className="w-[100px]">Order ID</TableHead>
              <TableHead>Description</TableHead>
              <TableHead>Assigned Agent</TableHead>
              <TableHead>Status</TableHead>
              <TableHead className="w-[180px]">Actions</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {orders.map((order) => (
              <TableRow key={order.id}>
                <TableCell className="font-mono text-sm">{order.id}</TableCell>
                <TableCell className="max-w-[250px] truncate">{order.description}</TableCell>
                <TableCell>{order.assignedAgentName || '-'}</TableCell>
                <TableCell>{getStatusBadge(order.status)}</TableCell>
                <TableCell>
                  <Button
                    size="sm"
                    variant="outline"
                    onClick={() => handleRequestSuggestion(order.id)}
                    disabled={
                      requestingOrderId === order.id ||
                      order.status === 'DELIVERED' ||
                      order.status === 'CANCELLED'
                    }
                  >
                    {requestingOrderId === order.id ? 'Requesting...' : 'Get Suggestion'}
                  </Button>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}

      <div className="text-xs text-muted-foreground">
        Tip: Click "Get Suggestion" to request an AI-powered reassignment recommendation for any order.
      </div>

      <Dialog open={isAddDialogOpen} onOpenChange={setIsAddDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Add New Order</DialogTitle>
            <DialogDescription>
              Create a new delivery order and assign it to an agent.
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-4 py-4">
            <div className="space-y-2">
              <label className="text-sm font-medium">Order ID</label>
              <input
                type="text"
                value={newOrderId}
                onChange={(e) => setNewOrderId(e.target.value)}
                placeholder="e.g., ORD-009"
                className="w-full px-3 py-2 border rounded-md text-sm"
              />
            </div>

            <div className="space-y-2">
              <label className="text-sm font-medium">Description</label>
              <input
                type="text"
                value={newOrderDescription}
                onChange={(e) => setNewOrderDescription(e.target.value)}
                placeholder="e.g., Electronics — Koramangala to Indiranagar"
                className="w-full px-3 py-2 border rounded-md text-sm"
              />
            </div>

            <div className="space-y-2">
              <label className="text-sm font-medium">Assign to Agent (Optional)</label>
              <select
                value={newOrderAgentId}
                onChange={(e) => setNewOrderAgentId(e.target.value)}
                className="w-full px-3 py-2 border rounded-md text-sm"
              >
                <option value="">Unassigned</option>
                {availableAgents.map((agent) => (
                  <option key={agent.id} value={agent.id}>
                    {agent.name} ({agent.id}) - {agent.activeOrderCount} orders
                  </option>
                ))}
              </select>
            </div>
          </div>

          <DialogFooter>
            <Button variant="outline" onClick={() => setIsAddDialogOpen(false)}>
              Cancel
            </Button>
            <Button
              onClick={handleCreateOrder}
              disabled={!newOrderId.trim() || !newOrderDescription.trim() || isCreating}
            >
              {isCreating ? 'Creating...' : 'Create Order'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
