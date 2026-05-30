import { useState } from 'react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Textarea } from '@/components/ui/textarea';
import type { Suggestion, TriggerReason } from '@/types';
import { updateSuggestionStatus } from '@/api/client';

interface SuggestionCardProps {
  suggestion: Suggestion;
  onUpdate: () => void;
}

function getTriggerBadge(trigger: TriggerReason) {
  switch (trigger) {
    case 'AGENT_OFFLINE':
      return <Badge variant="destructive" className="ml-2">Re-plan</Badge>;
    case 'SUGGESTION_REJECTED':
      return <Badge variant="secondary" className="ml-2">Retry</Badge>;
    case 'MANUAL_REQUEST':
      return <Badge variant="outline" className="ml-2">Manual</Badge>;
    default:
      return null;
  }
}

export function SuggestionCard({ suggestion, onUpdate }: SuggestionCardProps) {
  const [isRejectDialogOpen, setIsRejectDialogOpen] = useState(false);
  const [feedback, setFeedback] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [isWaitingForNewSuggestion, setIsWaitingForNewSuggestion] = useState(false);

  const handleAccept = async () => {
    setIsLoading(true);
    try {
      await updateSuggestionStatus(suggestion.id, { status: 'ACCEPTED' });
      onUpdate();
    } catch (error) {
      console.error('Failed to accept suggestion:', error);
    } finally {
      setIsLoading(false);
    }
  };

  const handleReject = async () => {
    if (!feedback.trim()) return;
    setIsLoading(true);
    try {
      await updateSuggestionStatus(suggestion.id, {
        status: 'REJECTED',
        feedback: feedback.trim(),
      });
      setIsRejectDialogOpen(false);
      setFeedback('');
      setIsLoading(false);
      setIsWaitingForNewSuggestion(true);

      // Wait 5 seconds for new suggestion to be generated, then refresh
      setTimeout(() => {
        setIsWaitingForNewSuggestion(false);
        onUpdate();
      }, 5000);
    } catch (error) {
      console.error('Failed to reject suggestion:', error);
      setIsLoading(false);
    }
  };

  if (isWaitingForNewSuggestion) {
    return (
      <Card className="mt-2">
        <CardContent className="py-6">
          <div className="flex flex-col items-center justify-center gap-3">
            <div className="animate-spin rounded-full h-6 w-6 border-b-2 border-primary"></div>
            <p className="text-sm text-muted-foreground">Generating new suggestion...</p>
          </div>
        </CardContent>
      </Card>
    );
  }

  return (
    <>
      <Card className="mt-2">
        <CardHeader className="py-3">
          <CardTitle className="text-sm flex items-center">
            AI Suggestion
            {getTriggerBadge(suggestion.triggerReason)}
          </CardTitle>
        </CardHeader>
        <CardContent className="py-2">
          <div className="space-y-2 text-sm">
            <div className="flex justify-between">
              <span className="text-muted-foreground">Recommended Agent:</span>
              <span className="font-medium">{suggestion.recommendedAgentName}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-muted-foreground">Confidence:</span>
              <span className="font-medium">{(suggestion.confidence * 100).toFixed(0)}%</span>
            </div>
            <div>
              <span className="text-muted-foreground">Reasoning:</span>
              <p className="mt-1 text-xs bg-muted p-2 rounded">{suggestion.reasoning}</p>
            </div>
          </div>
          <div className="flex gap-2 mt-4">
            <Button
              size="sm"
              onClick={handleAccept}
              disabled={isLoading}
              className="flex-1"
            >
              Accept
            </Button>
            <Button
              size="sm"
              variant="outline"
              onClick={() => setIsRejectDialogOpen(true)}
              disabled={isLoading}
              className="flex-1"
            >
              Reject
            </Button>
          </div>
        </CardContent>
      </Card>

      <Dialog open={isRejectDialogOpen} onOpenChange={setIsRejectDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Reject Suggestion</DialogTitle>
            <DialogDescription>
              Provide feedback for why this suggestion is not suitable.
              A new suggestion will be generated based on your feedback.
            </DialogDescription>
          </DialogHeader>
          <Textarea
            placeholder="e.g., Agent is too far from pickup location"
            value={feedback}
            onChange={(e) => setFeedback(e.target.value)}
            className="min-h-[100px]"
          />
          <DialogFooter>
            <Button
              variant="outline"
              onClick={() => setIsRejectDialogOpen(false)}
            >
              Cancel
            </Button>
            <Button
              variant="destructive"
              onClick={handleReject}
              disabled={!feedback.trim() || isLoading}
            >
              Reject & Retry
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
}
