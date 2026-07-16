# Modular monolith

The SRM Credit Engine is a modular monolith because the 3–4 day delivery needs strong transactional consistency and fast iteration without distributed-operation cost. Domain Modules keep ownership and Interfaces explicit so a future scaling or team-ownership need can justify extraction without pretending microservices already exist.
