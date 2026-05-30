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
import { getAgents, updateAgentStatus } from '@/api/client';
import type { Agent, AgentStatus } from '@/types';

interface AgentListProps {
  onAgentStatusChange?: () => void;
}

function getStatusBadge(status: AgentStatus) {
  switch (status) {
    case 'AVAILABLE':
      return <Badge className="bg-green-500 hover:bg-green-600">Available</Badge>;
    case 'BUSY':
      return <Badge className="bg-yellow-500 hover:bg-yellow-600">Busy</Badge>;
    case 'OFFLINE':
      return <Badge variant="destructive">Offline</Badge>;
    default:
      return <Badge variant="secondary">{status}</Badge>;
  }
}

export function AgentList({ onAgentStatusChange }: AgentListProps) {
  const [agents, setAgents] = useState<Agent[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [togglingAgentId, setTogglingAgentId] = useState<string | null>(null);

  const fetchAgents = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const data = await getAgents();
      setAgents(data);
    } catch (err) {
      setError('Failed to load agents');
      console.error(err);
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchAgents();
  }, [fetchAgents]);

  const handleToggleStatus = async (agent: Agent) => {
    const newStatus: AgentStatus = agent.status === 'OFFLINE' ? 'AVAILABLE' : 'OFFLINE';
    setTogglingAgentId(agent.id);
    try {
      await updateAgentStatus(agent.id, newStatus);
      await fetchAgents();
      onAgentStatusChange?.();
    } catch (err) {
      console.error('Failed to update agent status:', err);
    } finally {
      setTogglingAgentId(null);
    }
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
        <Button variant="outline" className="ml-4" onClick={fetchAgents}>
          Retry
        </Button>
      </div>
    );
  }

  const availableCount = agents.filter((a) => a.status === 'AVAILABLE').length;
  const busyCount = agents.filter((a) => a.status === 'BUSY').length;
  const offlineCount = agents.filter((a) => a.status === 'OFFLINE').length;

  return (
    <div className="space-y-4">
      <div className="flex justify-between items-center">
        <div className="flex items-center gap-4">
          <h2 className="text-lg font-semibold">Agents ({agents.length})</h2>
          <div className="flex gap-2 text-sm">
            <Badge className="bg-green-500">{availableCount} Available</Badge>
            <Badge className="bg-yellow-500">{busyCount} Busy</Badge>
            <Badge variant="destructive">{offlineCount} Offline</Badge>
          </div>
        </div>
        <Button variant="outline" size="sm" onClick={fetchAgents}>
          Refresh
        </Button>
      </div>

      <Table>
        <TableHeader>
          <TableRow>
            <TableHead className="w-[100px]">Agent ID</TableHead>
            <TableHead>Name</TableHead>
            <TableHead>Status</TableHead>
            <TableHead>Active Orders</TableHead>
            <TableHead>Zone</TableHead>
            <TableHead className="w-[150px]">Actions</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {agents.map((agent) => (
            <TableRow key={agent.id}>
              <TableCell className="font-mono text-sm">{agent.id}</TableCell>
              <TableCell className="font-medium">{agent.name}</TableCell>
              <TableCell>{getStatusBadge(agent.status)}</TableCell>
              <TableCell>{agent.activeOrderCount}</TableCell>
              <TableCell>{agent.currentZone || '-'}</TableCell>
              <TableCell>
                <Button
                  size="sm"
                  variant={agent.status === 'OFFLINE' ? 'default' : 'destructive'}
                  onClick={() => handleToggleStatus(agent)}
                  disabled={togglingAgentId === agent.id}
                >
                  {togglingAgentId === agent.id
                    ? 'Updating...'
                    : agent.status === 'OFFLINE'
                    ? 'Set Online'
                    : 'Set Offline'}
                </Button>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>

      <div className="text-xs text-muted-foreground">
        Tip: Set an agent offline to trigger the agentic re-planning loop for their assigned orders.
      </div>
    </div>
  );
}
