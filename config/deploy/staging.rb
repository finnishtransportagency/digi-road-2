role :app, %w{web@gateway}
role :web, %w{web@gateway}
server 'gateway', user: 'web', roles: %w{web app}

namespace :deploy do
  task :start_vallu_server do
    on roles(:all) do
      execute "killall -q node; exit 0"
      # Capistrano kills the vallu server before it gets to start up if sleep 1 is not defined
      execute "cd /home/web/digiroad2/current/; (nohup grunt vallu-test-server >> ./src/main/webapp/vallu-server.log 2>&1 &); sleep 2"
    end
  end

  after :publishing, :start_vallu_server
end