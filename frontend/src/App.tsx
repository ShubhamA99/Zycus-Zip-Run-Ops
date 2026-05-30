import { useState, useCallback } from 'react';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { AllOrdersList } from '@/components/AllOrdersList';
import { OrderList } from '@/components/OrderList';
import { AgentList } from '@/components/AgentList';

function App() {
  const [refreshKey, setRefreshKey] = useState(0);

  const triggerRefresh = useCallback(() => {
    setRefreshKey((k) => k + 1);
  }, []);

  return (
    <div className="min-h-screen bg-background">
      <header className="border-b bg-card">
        <div className="container py-4">
          <div className="flex items-center justify-between">
            <div>
              <h1 className="text-2xl font-bold">ZipRun Ops Dashboard</h1>
              <p className="text-sm text-muted-foreground">
                AI-Powered Reassignment Engine
              </p>
            </div>
            <div className="text-sm text-muted-foreground">
              Sprint 1 Demo
            </div>
          </div>
        </div>
      </header>

      <main className="container py-6">
        <Tabs defaultValue="all-orders" className="space-y-4">
          <TabsList>
            <TabsTrigger value="all-orders">Orders</TabsTrigger>
            <TabsTrigger value="pending">Pending Reassignments</TabsTrigger>
            <TabsTrigger value="agents">Agents</TabsTrigger>
          </TabsList>

          <TabsContent value="all-orders" className="space-y-4">
            <AllOrdersList key={`all-orders-${refreshKey}`} onRefresh={triggerRefresh} />
          </TabsContent>

          <TabsContent value="pending" className="space-y-4">
            <OrderList key={`pending-${refreshKey}`} onRefresh={triggerRefresh} />
          </TabsContent>

          <TabsContent value="agents" className="space-y-4">
            <AgentList
              key={`agents-${refreshKey}`}
              onAgentStatusChange={triggerRefresh}
            />
          </TabsContent>
        </Tabs>
      </main>

      <footer className="border-t mt-auto">
        <div className="container py-4 text-center text-sm text-muted-foreground">
          ZipRun AI Reassignment Engine &middot; Built for Hackathon
        </div>
      </footer>
    </div>
  );
}

export default App;
